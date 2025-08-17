package com.shaynesullivan.stockshark;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextWatcher;
import android.text.Editable;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

/**
 * CreateAccountActivity handles new user registration for the Stock Shark application.
 * This activity provides a secure account creation process with proper input validation,
 * password strength requirements, and database error handling.
 *
 * Security features include password hashing, input sanitization, and protection
 * against common vulnerabilities like SQL injection and weak passwords.
 */
public class CreateAccountActivity extends AppCompatActivity {

    // Validation rules - loaded from resources
    private int minUsernameLength;
    private int maxUsernameLength;
    private int minPasswordLength;
    private int maxPasswordLength;

    // Error message constants
    private static final String ERROR_FIELDS_REQUIRED = "Please enter both username and password";
    private static final String ERROR_USERNAME_EXISTS = "Username already exists. Choose another.";
    private static final String ERROR_ACCOUNT_CREATION = "Error creating account. Please try again.";
    private String errorUsernameLength;
    private String errorPasswordLength;
    private static final String ERROR_PASSWORD_WEAK = "Password must contain at least one uppercase letter, one lowercase letter, and one number";
    private static final String ERROR_USERNAME_INVALID = "Username can only contain letters, numbers, and underscores";
    private static final String ERROR_DATABASE_ERROR = "Database error occurred. Please try again.";
    private static final String SUCCESS_ACCOUNT_CREATED = "Account created successfully!";

    // UI Components
    private EditText etUsername;
    private EditText etPassword;
    private Button btnCreate;

    // Database helper
    private UserDatabase dbHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_account);

        loadValidationConstants();

        initializeComponents();
        initializeDatabase();
        setupCreateAccountButton();
    }

    /**
     * Load validation constants from resources
     */
    private void loadValidationConstants() {
        minUsernameLength = getResources().getInteger(R.integer.min_username_length);
        maxUsernameLength = getResources().getInteger(R.integer.max_username_length);
        minPasswordLength = getResources().getInteger(R.integer.min_password_length);
        maxPasswordLength = getResources().getInteger(R.integer.max_password_length);

        // Build dynamic error messages
        errorUsernameLength = "Username must be " + minUsernameLength + "-" + maxUsernameLength + " characters";
        errorPasswordLength = "Password must be at least " + minPasswordLength + " characters";
    }

    /**
     * Initialize UI components
     */
    private void initializeComponents() {
        etUsername = findViewById(R.id.etUsername);
        etPassword = findViewById(R.id.etPassword);
        btnCreate = findViewById(R.id.btnCreateAccount);

        // Add text watchers for real-time validation
        TextWatcher inputWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                validateInputFields(); // Enable/disable button as user types
            }

            @Override
            public void afterTextChanged(Editable s) {}
        };

        etUsername.addTextChangedListener(inputWatcher);
        etPassword.addTextChangedListener(inputWatcher);
    }

    /**
     * Validate input fields in real-time and enable/disable the create button
     */
    private void validateInputFields() {
        String username = etUsername.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        // Basic checks for enabling the button
        boolean hasUsername = !username.isEmpty() && username.length() >= minUsernameLength;
        boolean hasPassword = !password.isEmpty() && password.length() >= minPasswordLength;
        boolean isValid = hasUsername && hasPassword;

        // Enable/disable button and adjust appearance
        btnCreate.setEnabled(isValid);
        btnCreate.setAlpha(isValid ? 1.0f : 0.6f);
    }

    /**
     * Initialize database helper
     */
    private void initializeDatabase() {
        dbHelper = new UserDatabase(this);
    }

    /**
     * Setup create account button click listener
     */
    private void setupCreateAccountButton() {
        btnCreate.setOnClickListener(view -> handleCreateAccountAttempt());
    }

    /**
     * Handle account creation attempt with comprehensive validation
     */
    private void handleCreateAccountAttempt() {
        String username = etUsername.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        // Validate input fields
        if (!validateInput(username, password)) {
            return;
        }

        // Validate username requirements
        if (!validateUsername(username)) {
            return;
        }

        // Validate password requirements
        if (!validatePassword(password)) {
            return;
        }

        // Check if username already exists
        if (isUsernameExists(username)) {
            return;
        }

        // Create the account
        createUserAccount(username, password);
    }

    /**
     * Validate that input fields are not empty
     *
     * @param username The username to validate
     * @param password The password to validate
     * @return true if valid, false otherwise
     */
    private boolean validateInput(String username, String password) {
        if (username.isEmpty() || password.isEmpty()) {
            showToast(ERROR_FIELDS_REQUIRED);
            return false;
        }
        return true;
    }

    /**
     * Validate username format and length requirements
     *
     * @param username The username to validate
     * @return true if valid, false otherwise
     */
    private boolean validateUsername(String username) {
        // Check length requirements
        if (username.length() < minUsernameLength || username.length() > maxUsernameLength) {
            showToast(errorUsernameLength);
            return false;
        }

        // Check for valid characters (letters, numbers, underscores only)
        if (!username.matches("^[a-zA-Z0-9_]+$")) {
            showToast(ERROR_USERNAME_INVALID);
            return false;
        }

        return true;
    }

    /**
     * Validate password strength requirements
     *
     * @param password The password to validate
     * @return true if valid, false otherwise
     */
    private boolean validatePassword(String password) {
        // Check minimum length
        if (password.length() < minPasswordLength) {
            showToast(errorPasswordLength);
            return false;
        }

        // Check maximum length to prevent buffer overflow attacks
        if (password.length() > maxPasswordLength) {
            showToast(errorPasswordLength);
            return false;
        }

        // Check for strong password requirements
        if (!isPasswordStrong(password)) {
            showToast(ERROR_PASSWORD_WEAK);
            return false;
        }

        return true;
    }

    /**
     * Check if password meets strength requirements
     *
     * @param password The password to check
     * @return true if password is strong, false otherwise
     */
    private boolean isPasswordStrong(String password) {
        boolean hasUppercase = password.matches(".*[A-Z].*");
        boolean hasLowercase = password.matches(".*[a-z].*");
        boolean hasNumber = password.matches(".*\\d.*");

        return hasUppercase && hasLowercase && hasNumber;
    }

    /**
     * Check if username already exists in the database
     *
     * @param username The username to check
     * @return true if username exists, false otherwise
     */
    private boolean isUsernameExists(String username) {
        try {
            if (dbHelper.userExists(username)) {
                showToast(ERROR_USERNAME_EXISTS);
                return true;
            }
        } catch (Exception e) {
            showToast(ERROR_DATABASE_ERROR);
            return true; // Treat database errors as blocking
        }
        return false;
    }

    /**
     * Create a new user account in the database
     *
     * @param username The username for the new account
     * @param password The password for the new account
     */
    private void createUserAccount(String username, String password) {
        try {
            // Hash the password before storing
            long result = dbHelper.addUser(username, password);

            if (result != -1) {
                showToast(SUCCESS_ACCOUNT_CREATED);
                navigateToLogin();
            } else {
                showToast(ERROR_ACCOUNT_CREATION);
            }
        } catch (Exception e) {
            showToast(ERROR_DATABASE_ERROR);
        }
    }

    /**
     * Navigate to login activity after successful account creation
     */
    private void navigateToLogin() {
        Intent intent = new Intent(this, LoginActivity.class);
        startActivity(intent);
        finish(); // Close this activity
    }

    /**
     * Utility method to show toast messages
     *
     * @param message The message to display
     */
    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    /**
     * Clean up resources when activity is destroyed
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (dbHelper != null) {
            dbHelper.close();
        }
    }
}