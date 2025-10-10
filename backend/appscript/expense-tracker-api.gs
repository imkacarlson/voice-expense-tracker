/**
 * Expense Tracker API for Google Sheets
 * Handles POST requests from Android app to add expense entries
 */

function doPost(e) {
  try {
    // Get all configuration from Script Properties
    const scriptProperties = PropertiesService.getScriptProperties();
    const CONFIG = {
      SPREADSHEET_ID: scriptProperties.getProperty('SPREADSHEET_ID'),
      SHEET_NAME: scriptProperties.getProperty('SHEET_NAME'),
      ALLOWED_EMAIL: scriptProperties.getProperty('ALLOWED_EMAIL'),
      AUTH_TOKEN: scriptProperties.getProperty('AUTH_TOKEN')
    };
    
    // Verify configuration exists
    if (!CONFIG.SPREADSHEET_ID || !CONFIG.SHEET_NAME || !CONFIG.ALLOWED_EMAIL) {
      console.error('Missing required Script Properties');
      return createResponse('error', 'Server configuration error - missing properties');
    }
    
    // Parse incoming data
    const data = JSON.parse(e.postData.contents);
    
    // Verify authentication
    const userEmail = verifyAuthentication(data.token, CONFIG);
    
    // Check if it's your account
    if (userEmail !== CONFIG.ALLOWED_EMAIL) {
      console.log(`Unauthorized access attempt from: ${userEmail}`);
      return createResponse('error', 'Unauthorized: Only the owner can add expenses');
    }
    
    // Get the specific sheet within the spreadsheet
    const spreadsheet = SpreadsheetApp.openById(CONFIG.SPREADSHEET_ID);
    const sheet = spreadsheet.getSheetByName(CONFIG.SHEET_NAME);
    
    if (!sheet) {
      return createResponse('error', `Sheet "${CONFIG.SHEET_NAME}" not found in spreadsheet`);
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
    console.log(`New expense added by ${userEmail} at ${new Date().toISOString()}`);
    console.log(`Description: ${data.description}, Amount: ${data.amount}`);
    
    return createResponse('success', 'Expense added successfully', {
      timestamp: Utilities.formatDate(now, tz, "M/d/yyyy HH:mm:ss"),
      description: data.description,
      amount: data.amount,
      rowNumber: sheet.getLastRow()
    });
    
  } catch(error) {
    console.error('Error in doPost:', error.toString());
    return createResponse('error', error.toString());
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
      console.log(`Authenticated via access token: ${tokenInfo.email}`);
      return tokenInfo.email;
    }
    
    // Try ID token verification if access token fails
    const idTokenResponse = UrlFetchApp.fetch(
      `https://oauth2.googleapis.com/tokeninfo?id_token=${token}`,
      {muteHttpExceptions: true}
    );
    
    if (idTokenResponse.getResponseCode() === 200) {
      const idTokenInfo = JSON.parse(idTokenResponse.getContentText());
      console.log(`Authenticated via ID token: ${idTokenInfo.email}`);
      return idTokenInfo.email;
    }
    
    // If OAuth fails and backup token exists, try that
    if (CONFIG.AUTH_TOKEN && token === CONFIG.AUTH_TOKEN) {
      console.log('Authenticated via backup token');
      return CONFIG.ALLOWED_EMAIL;
    }
    
    throw new Error('All authentication methods failed');
    
  } catch(error) {
    // Last resort: check backup token
    if (CONFIG.AUTH_TOKEN && token === CONFIG.AUTH_TOKEN) {
      console.log('Authenticated via backup token (after error)');
      return CONFIG.ALLOWED_EMAIL;
    }
    
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
    if (key === 'AUTH_TOKEN') {
      console.log(`${key}: [HIDDEN FOR SECURITY]`);
    } else {
      console.log(`${key}: ${properties[key]}`);
    }
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
 */
function testAddExpense() {
  // Simulate a POST request
  const testRequest = {
    postData: {
      contents: JSON.stringify({
        token: PropertiesService.getScriptProperties().getProperty('AUTH_TOKEN'),
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
