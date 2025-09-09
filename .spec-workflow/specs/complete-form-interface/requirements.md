# Complete Form Interface Implementation - Requirements

## Overview

Transform the current basic EditText form interface into a comprehensive, production-ready transaction form that perfectly matches the CSV structure, provides excellent UX, and includes full configuration management.

## Current State Analysis

### ✅ **Already Working**
- Basic form with EditText fields for all major transaction properties
- AI parsing pre-fills form fields with parsed data
- Manual editing and confirmation flow
- Navigation back to MainActivity
- Transaction persistence and sync

### ⚠️ **Gaps Identified**
- **Missing Fields**: Transfer Category, Transfer Destination not in current form
- **Wrong Input Types**: All fields are EditText - need dropdowns, date pickers, multi-select
- **No Configuration**: Dropdown options are hardcoded, not user-configurable  
- **Limited Validation**: Basic required field checks, missing business logic validation
- **Poor UX**: No conditional field visibility, no smart defaults

## Business Requirements

### **BR-1: Complete CSV Field Coverage**
The form must support ALL columns from the user's actual CSV structure:

1. **Date** - User-selectable date (can be backdated)
2. **Amount** - User's personal share (required for expenses/income)
3. **Description** - Merchant/description (required)
4. **Type** - Expense | Income | Transfer (required)
5. **Expense Category** - Configurable dropdown (required for expenses)
6. **Tags** - Multi-select configurable options (optional)
7. **Income Category** - Configurable dropdown (required for income) 
8. **Account/Credit Card** - Configurable dropdown (optional)
9. **Overall Charged** - Total amount for splitwise transactions (optional)
10. **Transfer Category** - Configurable dropdown (required for transfers)
11. **Transfer Destination** - Configurable dropdown (required for transfers)

### **BR-2: Proper Input Types**
- **Date fields**: Native Android DatePicker
- **Amount fields**: Numeric input with decimal support
- **Dropdowns**: Spinner components with configurable options
- **Multi-select**: Custom component for tags
- **Text fields**: Only for Description and Note

### **BR-3: Smart Form Behavior**
- **Conditional Visibility**: Show only relevant fields based on transaction type
- **Smart Defaults**: Auto-populate date with today, pre-select common categories
- **Real-time Validation**: Show validation errors as user types
- **Field Dependencies**: Transfer requires both source and destination accounts

### **BR-4: Configuration Management**
Users must be able to configure all dropdown options through settings:
- Add new categories, accounts, tags
- Edit existing options
- Remove unused options  
- Set default selections
- Export/import configurations

### **BR-5: Validation Rules**
- **Required Fields**: Amount, Description, Category (based on type)
- **Business Logic**: Overall Charged ≥ Amount for splitwise
- **Data Types**: Numeric validation for amounts, date validation
- **Consistency**: Transfer requires both accounts, expense requires expense category

## Functional Requirements

### **FR-1: Enhanced Form Interface**

#### **FR-1.1: Date Selection**
```
GIVEN user is editing a transaction
WHEN user taps the Date field  
THEN a DatePicker dialog opens
AND defaults to today's date
AND allows selecting any past or future date
AND updates the field with selected date in MM/DD format
```

#### **FR-1.2: Amount Input**
```  
GIVEN user is editing an expense or income
WHEN user taps Amount field
THEN numeric keyboard opens
AND allows decimal input (e.g., 23.45)
AND validates positive numbers only
AND shows validation error for invalid input
```

#### **FR-1.3: Type Selection with Conditional Fields**
```
GIVEN user selects transaction type
WHEN type is "Expense"
THEN show: Amount, Expense Category, Tags, Account, Overall Charged
AND hide: Income Category, Transfer Category, Transfer Destination

WHEN type is "Income"  
THEN show: Amount, Income Category, Tags, Account
AND hide: Expense Category, Overall Charged, Transfer Category, Transfer Destination

WHEN type is "Transfer"
THEN show: Transfer Category, Transfer Destination, Tags
AND hide: Amount, Expense Category, Income Category, Account, Overall Charged
```

#### **FR-1.4: Configurable Dropdowns**
```
GIVEN user taps any dropdown field (Category, Account, etc.)
WHEN dropdown opens
THEN shows user-configured options for that field type
AND includes "+ Add New" option at bottom
AND allows selecting existing option
AND saves selection to transaction
```

#### **FR-1.5: Multi-Select Tags**
```
GIVEN user taps Tags field
WHEN tags selector opens
THEN shows checkboxes for all user-configured tag options
AND allows selecting multiple tags
AND shows selected tags as chips in the field
AND allows deselecting tags
```

### **FR-2: Settings Configuration**

#### **FR-2.1: Dropdown Management**
```
GIVEN user opens Settings → Dropdown Configuration
WHEN user selects a field type (Expense Categories, Accounts, etc.)
THEN shows list of current options for that field
AND allows adding new options
AND allows editing existing options
AND allows deleting unused options
AND allows reordering options
```

#### **FR-2.2: Default Values**
```
GIVEN user configures dropdown options
WHEN user sets a default value
THEN that option is pre-selected in new transactions
AND user can change defaults at any time
AND defaults apply only to new transactions
```

### **FR-3: Validation System**

#### **FR-3.1: Real-Time Validation**
```
GIVEN user is editing form fields
WHEN user enters invalid data
THEN field shows red border and error message immediately
AND error message explains the specific problem
AND form Confirm button remains disabled until valid
```

#### **FR-3.2: Business Logic Validation**
```
GIVEN user enters splitwise transaction
WHEN Overall Charged < Amount
THEN shows error "Overall charged must be ≥ your share amount"
AND prevents submission until corrected

GIVEN user selects Transfer type
WHEN Transfer Category or Transfer Destination is empty
THEN shows error "Both transfer accounts are required"
AND prevents submission until both selected
```

### **FR-4: Data Migration**

#### **FR-4.1: Backwards Compatibility**
```
GIVEN existing transactions in database
WHEN app is updated with new form fields
THEN existing transactions load correctly
AND new fields show as empty/default values
AND user can edit and save existing transactions
```

## Technical Requirements

### **TR-1: UI Components**
- **DatePickerDialog**: Native Android date selection
- **Spinner**: Dropdown selection with custom adapter
- **MultiSelectDialog**: Custom component for tag selection
- **EditText**: Numeric input with input validation
- **ScrollView**: Form scrolling for smaller screens

### **TR-2: Data Storage**
- **Room Database**: Store dropdown configurations
- **SharedPreferences**: Store default selections and settings
- **Migration Scripts**: Handle database schema updates
- **Validation**: Ensure data consistency across updates

### **TR-3: Configuration Architecture**
- **ConfigRepository**: Centralized access to dropdown options
- **DropdownConfig**: Data model for field configurations
- **DefaultValuesManager**: Handle default selections
- **ValidationEngine**: Centralized validation logic

### **TR-4: Form State Management**
- **ViewModel**: Handle form state and validation
- **Two-way Data Binding**: Sync form fields with transaction model
- **Conditional Rendering**: Show/hide fields based on type
- **Error State**: Track and display validation errors

## User Experience Requirements

### **UX-1: Intuitive Flow**
- Form fields appear in logical order matching user mental model
- Required fields are clearly marked with visual indicators
- Validation errors are helpful and actionable
- Form submission is only possible when valid

### **UX-2: Efficient Input**
- Smart defaults minimize user input required
- Dropdowns show most-used options first
- Form remembers recent selections
- Voice corrections still available as alternative

### **UX-3: Configuration Discovery**
- Settings clearly indicate which fields are configurable
- Easy access to add new options from form context
- Configuration changes take effect immediately
- Clear indication of current defaults

## Success Criteria

### **Acceptance Criteria**
- [ ] All CSV columns have corresponding form fields with correct input types
- [ ] Form shows only relevant fields based on transaction type
- [ ] All dropdown options are configurable by user in settings
- [ ] Real-time validation prevents invalid submissions
- [ ] Business logic validation ensures data consistency
- [ ] Existing transactions continue to work after upgrade
- [ ] Form pre-populates with AI parsing results but allows full manual override
- [ ] User can complete any transaction type using only form interface

### **Performance Criteria**
- Form opens and renders within 500ms
- Dropdown configuration changes save within 200ms
- Form validation responds to input within 100ms
- No memory leaks or performance degradation with large dropdown lists

### **Usability Criteria**
- New users can configure basic categories within 2 minutes
- Power users can create complex transactions with 20+ tags efficiently
- Form works correctly on various screen sizes and orientations
- Accessible to users with disabilities (screen readers, large text)

## Out of Scope

### **Not Included in This Spec**
- Advanced validation (duplicate detection, spending limits)
- Bulk import/export of transaction data
- Integration with external services (bank feeds, receipt scanning)
- Multi-currency support
- Advanced AI improvements or prompting changes
- Voice interface improvements (covered separately)

### **Future Enhancements**
- Smart category suggestions based on merchant
- Spending analytics integration
- Backup/restore of configurations
- Templates for common transaction types

## Dependencies

### **Internal Dependencies**
- Current Transaction data model and database schema
- TransactionConfirmationActivity UI implementation
- AI parsing system (provides initial field values)
- Settings infrastructure

### **External Dependencies**
- Android DatePickerDialog (API 21+)
- Material Design Components (dropdowns, chips)
- Room Database migration system
- SharedPreferences for configuration storage

## Risk Assessment

### **Technical Risks**
- **Database Migration**: Risk of data loss when adding new fields
  - *Mitigation*: Comprehensive migration testing, backup strategies
- **Performance**: Large dropdown lists could slow UI
  - *Mitigation*: Implement virtualized lists, lazy loading
- **Backwards Compatibility**: Changes might break existing flows
  - *Mitigation*: Extensive regression testing, feature flags

### **UX Risks**  
- **Complexity**: Too many configuration options might overwhelm users
  - *Mitigation*: Smart defaults, progressive disclosure, onboarding
- **Discovery**: Users might not find configuration options
  - *Mitigation*: In-context access, clear navigation, help text

## Implementation Priority

### **Phase 1: Core Form Enhancement** (High Priority)
- Add missing Transfer Category and Transfer Destination fields
- Implement conditional field visibility
- Convert Date field to DatePicker
- Basic dropdown implementation for categories

### **Phase 2: Configuration System** (High Priority)  
- Settings UI for dropdown management
- Default value configuration
- Data persistence for configurations

### **Phase 3: Validation & Polish** (Medium Priority)
- Real-time validation implementation
- Business logic validation
- Error messaging and UX improvements
- Multi-select tags implementation

### **Phase 4: Advanced Features** (Low Priority)
- Configuration import/export
- Advanced validation rules
- Performance optimizations
- Accessibility improvements