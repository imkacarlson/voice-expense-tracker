/**
 * Expense Tracker API for Google Sheets
 * Handles POST requests from Android app to add expense entries
 */

function doPost(e) {
  try {
    // Validate request size to prevent DoS attacks
    const MAX_REQUEST_SIZE = 10000; // 10KB - sufficient for expense data
    if (e.postData.contents.length > MAX_REQUEST_SIZE) {
      console.log(`Request too large: ${e.postData.contents.length} bytes`);
      writeLog('WARN', 'Request too large', `Size: ${e.postData.contents.length} bytes`);
      return createResponse('error', 'Request too large');
    }

    // Get all configuration from Script Properties
    const scriptProperties = PropertiesService.getScriptProperties();
    const CONFIG = {
      SPREADSHEET_ID: scriptProperties.getProperty('SPREADSHEET_ID'),
      SHEET_NAME: scriptProperties.getProperty('SHEET_NAME'),
      ALLOWED_EMAIL: scriptProperties.getProperty('ALLOWED_EMAIL'),
      OAUTH_CLIENT_ID: scriptProperties.getProperty('OAUTH_CLIENT_ID')
    };

    // Verify configuration exists
    if (!CONFIG.SPREADSHEET_ID || !CONFIG.SHEET_NAME || !CONFIG.ALLOWED_EMAIL) {
      console.error('Missing required Script Properties');
      return createResponse('error', 'Server configuration error');
    }

    // Parse incoming data
    const data = JSON.parse(e.postData.contents);
    
    // Verify authentication
    const userEmail = verifyAuthentication(data.token, CONFIG);

    // Check if it's your account
    if (userEmail !== CONFIG.ALLOWED_EMAIL) {
      console.log(`Unauthorized access attempt from: ${userEmail}`);
      writeLog('WARN', 'Unauthorized access attempt', `Email: ${userEmail}`);
      return createResponse('error', 'Unauthorized: Only the owner can add expenses');
    }

    // Rate limiting: 30 requests per 10 minutes
    const cache = CacheService.getUserCache();
    const rateLimitKey = `rate_${userEmail.replace(/[^a-zA-Z0-9]/g, '_')}`;
    const requestCount = parseInt(cache.get(rateLimitKey) || '0');

    if (requestCount >= 30) {
      console.log(`Rate limit exceeded for user (${requestCount} requests in window)`);
      writeLog('WARN', 'Rate limit exceeded', `User: ${hashEmail(userEmail)}, Count: ${requestCount}`);
      return createResponse('error', 'Too many requests. Please wait a moment.');
    }

    // Increment request counter (10 minute expiration)
    cache.put(rateLimitKey, (requestCount + 1).toString(), 600);
    
    // Get the specific sheet within the spreadsheet
    const spreadsheet = SpreadsheetApp.openById(CONFIG.SPREADSHEET_ID);
    const sheet = spreadsheet.getSheetByName(CONFIG.SHEET_NAME);

    if (!sheet) {
      console.error(`Sheet "${CONFIG.SHEET_NAME}" not found in spreadsheet`);
      return createResponse('error', 'Sheet not found');
    }
    
    // Get spreadsheet timezone and set the number format for column A
    const tz = spreadsheet.getSpreadsheetTimeZone();
    sheet.getRange("A:A").setNumberFormat("M/d/yyyy HH:mm:ss");
    
    // Prepare the row data matching your column structure
    const now = new Date();  // Keep as Date object (not toLocaleString)
    const newRow = [
      now,                              // Timestamp (formatted by column)
      data.date || '',                 // Date
      data.amount || '',               // Amount (No $ sign)
      data.description || '',          // Description
      data.type || '',                 // Type
      data.expenseCategory || '',      // Expense Category
      data.tags || '',                 // Tags
      data.incomeCategory || '',       // Income Category
      '',                              // Empty column
      data.account || '',              // Account / Credit Card
      data.splitwiseAmount || '',      // [If splitwise] how much overall charged to my card?
      data.transferCategory || '',     // Transfer Category
      data.transferAccount || ''       // Account transfer is going into
    ];
    
    // Append the row to the sheet
    sheet.appendRow(newRow);
    
    // Log for debugging (visible in Apps Script editor logs)
    const userHash = hashEmail(userEmail);
    console.log(`New expense added by user ${userHash} at ${new Date().toISOString()}`);
    console.log(`Description: ${data.description}, Amount: ${data.amount}`);
    writeLog('INFO', 'Expense added successfully', `User: ${userHash}, Amount: ${data.amount}, Desc: ${data.description}`);
    const amountValue = (() => {
      if (data.amount === null || data.amount === undefined || data.amount === '') return null;
      const numeric = Number(data.amount);
      return Number.isFinite(numeric) ? numeric : null;
    })();

    return createResponse('success', 'Expense added successfully', {
      timestamp: Utilities.formatDate(now, tz, "M/d/yyyy HH:mm:ss"),
      description: data.description,
      amount: amountValue,
      rowNumber: sheet.getLastRow()
    });
    
  } catch (error) {
    const message = error && typeof error.toString === 'function' ? error.toString() : String(error);
    console.error('Error in doPost:', message);

    if (message.includes('Authentication failed')) {
      writeLog('ERROR', 'Authentication failed', 'Invalid or expired OAuth token.');
      return createResponse('error', 'invalid_token', {
        errorCode: 'AUTH_INVALID_TOKEN',
        detail: 'Invalid or expired OAuth token.'
      });
    }

    writeLog('ERROR', 'doPost failed', message);
    return createResponse('error', 'An error occurred. Please try again.');
  }
}

/**
 * Hash email for privacy in logs
 */
function hashEmail(email) {
  return Utilities.base64Encode(
    Utilities.computeDigest(Utilities.DigestAlgorithm.SHA_256, email)
  ).substring(0, 8);
}

/**
 * Write log entry to sheet (safe - won't break on errors)
 * @param {string} severity - INFO, WARN, ERROR
 * @param {string} message - Log message
 * @param {string} details - Optional additional details
 */
function writeLog(severity, message, details = '') {
  try {
    const scriptProperties = PropertiesService.getScriptProperties();
    const spreadsheetId = scriptProperties.getProperty('SPREADSHEET_ID');
    const logSheetName = scriptProperties.getProperty('LOG_SHEET_NAME') || 'API Logs';

    if (!spreadsheetId) return; // No spreadsheet configured, skip silently

    const spreadsheet = SpreadsheetApp.openById(spreadsheetId);
    let logSheet = spreadsheet.getSheetByName(logSheetName);

    // Create log sheet if it doesn't exist
    if (!logSheet) {
      logSheet = spreadsheet.insertSheet(logSheetName);
      logSheet.appendRow(['Timestamp', 'Severity', 'Message', 'Details']);
      logSheet.getRange('A1:D1').setFontWeight('bold').setBackground('#f3f3f3');
      logSheet.setFrozenRows(1);
    }

    // Append log entry
    logSheet.appendRow([
      new Date(),
      severity,
      message,
      details
    ]);

    // Keep only last 1000 log entries to prevent sheet bloat
    const lastRow = logSheet.getLastRow();
    if (lastRow > 1001) { // 1000 logs + 1 header
      logSheet.deleteRows(2, lastRow - 1001);
    }

  } catch (error) {
    // Logging failed - don't break the main function
    // Still write to console for debugging
    console.error('Failed to write log to sheet:', error.toString());
  }
}

/**
 * Verify authentication token
 */
function verifyAuthentication(token, CONFIG) {
  if (!token) {
    throw new Error('No authentication token provided');
  }
  
  try {
    // Try access token verification first
    const response = UrlFetchApp.fetch(
      `https://oauth2.googleapis.com/tokeninfo?access_token=${token}`,
      {muteHttpExceptions: true}
    );
    
    if (response.getResponseCode() === 200) {
      const tokenInfo = JSON.parse(response.getContentText());

      // Validate audience if CLIENT_ID is configured
      if (CONFIG.OAUTH_CLIENT_ID) {
        if (tokenInfo.aud) {
          if (tokenInfo.aud !== CONFIG.OAUTH_CLIENT_ID) {
            const error = 'Token not issued for this application';
            writeLog('ERROR', 'OAuth audience validation failed', `Expected: ${CONFIG.OAUTH_CLIENT_ID}, Got: ${tokenInfo.aud}`);
            throw new Error(error);
          }
          // Audience matches - log success
          writeLog('INFO', 'OAuth audience validated', `Client ID matched: ${tokenInfo.aud.substring(0, 20)}...`);
        } else {
          writeLog('WARN', 'OAuth audience check skipped', 'Token has no aud field');
        }
      }

      console.log(`Authenticated via access token: user ${hashEmail(tokenInfo.email)}`);
      return tokenInfo.email;
    }
    
    const accessStatus = response.getResponseCode();
    const accessBody = response.getContentText();
    const accessSnippet = accessBody.length > 400 ? `${accessBody.substring(0, 400)}...` : accessBody;
    writeLog('ERROR', 'Access token verification failed', `Status: ${accessStatus}, Body: ${accessSnippet}`);
    
    // Try ID token verification if access token fails
    const idTokenResponse = UrlFetchApp.fetch(
      `https://oauth2.googleapis.com/tokeninfo?id_token=${token}`,
      {muteHttpExceptions: true}
    );
    
    if (idTokenResponse.getResponseCode() === 200) {
      const idTokenInfo = JSON.parse(idTokenResponse.getContentText());

      // Validate audience if CLIENT_ID is configured
      if (CONFIG.OAUTH_CLIENT_ID) {
        if (idTokenInfo.aud) {
          if (idTokenInfo.aud !== CONFIG.OAUTH_CLIENT_ID) {
            const error = 'Token not issued for this application';
            writeLog('ERROR', 'OAuth audience validation failed', `Expected: ${CONFIG.OAUTH_CLIENT_ID}, Got: ${idTokenInfo.aud}`);
            throw new Error(error);
          }
          // Audience matches - log success
          writeLog('INFO', 'OAuth audience validated', `Client ID matched: ${idTokenInfo.aud.substring(0, 20)}...`);
        } else {
          writeLog('WARN', 'OAuth audience check skipped', 'Token has no aud field');
        }
      }

      console.log(`Authenticated via ID token: user ${hashEmail(idTokenInfo.email)}`);
      return idTokenInfo.email;
    }
    
    const idStatus = idTokenResponse.getResponseCode();
    const idBody = idTokenResponse.getContentText();
    const idSnippet = idBody.length > 400 ? `${idBody.substring(0, 400)}...` : idBody;
    writeLog('ERROR', 'ID token verification failed', `Status: ${idStatus}, Body: ${idSnippet}`);

    throw new Error('All authentication methods failed');

  } catch(error) {
    throw new Error('Authentication failed: ' + error.toString());
  }
}

/**
 * Create a JSON response
 */
function createResponse(status, message, data = null) {
  const response = {
    result: status,
    message: message,
    timestamp: new Date().toISOString()
  };
  
  if (data) {
    response.data = data;
  }
  
  return ContentService
    .createTextOutput(JSON.stringify(response))
    .setMimeType(ContentService.MimeType.JSON);
}

/**
 * GET endpoint for testing if API is running
 */
function doGet() {
  const scriptProperties = PropertiesService.getScriptProperties();
  const sheetName = scriptProperties.getProperty('SHEET_NAME');
  
  return ContentService
    .createTextOutput(`Expense Tracker API is running. Configured for sheet: "${sheetName}". Use POST to add expenses.`)
    .setMimeType(ContentService.MimeType.TEXT);
}

/**
 * Test function to verify Script Properties are set correctly
 * Run this in the Apps Script editor to check configuration
 */
function testConfiguration() {
  const scriptProperties = PropertiesService.getScriptProperties();
  const properties = scriptProperties.getProperties();
  
  console.log('=== Current Script Properties ===');
  for (const key in properties) {
    console.log(`${key}: ${properties[key]}`);
  }
  
  // Try to access the spreadsheet and sheet
  try {
    const spreadsheet = SpreadsheetApp.openById(properties.SPREADSHEET_ID);
    console.log(`✓ Spreadsheet found: ${spreadsheet.getName()}`);
    
    const sheet = spreadsheet.getSheetByName(properties.SHEET_NAME);
    if (sheet) {
      console.log(`✓ Sheet found: ${sheet.getName()}`);
      console.log(`  Current row count: ${sheet.getLastRow()}`);
      console.log(`  Current column count: ${sheet.getLastColumn()}`);
    } else {
      console.log(`✗ Sheet "${properties.SHEET_NAME}" not found`);
    }
  } catch(error) {
    console.log(`✗ Error accessing spreadsheet: ${error.toString()}`);
  }
}

/**
 * Test function to add a sample expense
 * Run this in the Apps Script editor to test without Android app
 * Note: You must provide a valid OAuth token to use this function
 */
function testAddExpense() {
  // Simulate a POST request
  const testRequest = {
    postData: {
      contents: JSON.stringify({
        token: 'YOUR_OAUTH_ACCESS_TOKEN_HERE', // Replace with a valid OAuth token
        date: '12/28/2024',
        amount: 45.99,
        description: 'Test expense from Apps Script',
        type: 'Expense',
        expenseCategory: 'Food',
        tags: 'test,debug',
        account: 'Test Card'
      })
    }
  };

  const response = doPost(testRequest);
  const responseText = response.getContent();
  console.log('Test response:', responseText);
}

/**
 * View recent entries (for debugging)
 */
function getRecentEntries(numRows = 5) {
  const scriptProperties = PropertiesService.getScriptProperties();
  const spreadsheet = SpreadsheetApp.openById(scriptProperties.getProperty('SPREADSHEET_ID'));
  const sheet = spreadsheet.getSheetByName(scriptProperties.getProperty('SHEET_NAME'));
  
  const lastRow = sheet.getLastRow();
  const startRow = Math.max(2, lastRow - numRows + 1); // Skip header row
  
  if (lastRow < 2) {
    console.log('No data entries found');
    return;
  }
  
  const range = sheet.getRange(startRow, 1, Math.min(numRows, lastRow - 1), 13);
  const values = range.getValues();
  
  console.log(`=== Last ${numRows} entries ===`);
  values.forEach((row, index) => {
    console.log(`Entry ${index + 1}:`);
    console.log(`  Date: ${row[1]}, Amount: ${row[2]}`);
    console.log(`  Description: ${row[3]}`);
    console.log(`  Category: ${row[5]}, Account: ${row[9]}`);
  });
}
