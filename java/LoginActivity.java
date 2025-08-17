package com.shaynesullivan.stockshark;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextWatcher;
import android.text.Editable;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * LoginActivity handles user authentication.
 */
public class LoginActivity extends AppCompatActivity {

    // UI component constants
    private static final String EXTRA_USER_ID = "USER_ID";

    // Security constants - loaded from resources
    private int maxLoginAttempts;
    private long lockoutDurationMs;
    private static final long LOGIN_DELAY_MS = 1000; // Minimum delay for security

    // Validation constants - loaded from resources
    private static final int MIN_INPUT_LENGTH = 1;
    private int maxInputLength;

    // Error messages
    private static final String ERROR_EMPTY_FIELDS = "Please enter both username and password";
    private static final String ERROR_INVALID_CREDENTIALS = "Invalid username or password";
    private static final String ERROR_TOO_MANY_ATTEMPTS = "Too many failed attempts. Please try again later.";
    private static final String ERROR_DATABASE_ERROR = "Unable to process login. Please try again.";
    private static final String ERROR_USER_ID_RETRIEVAL = "Login successful, but unable to retrieve user data";
    private static final String ERROR_INVALID_INPUT_LENGTH = "Input exceeds maximum allowed length";

    // Success messages
    private static final String SUCCESS_LOGIN = "Login successful!";

    // Logging tag
    private static final String TAG = "LoginActivity";

    // UI components
    private EditText etUsername;
    private EditText etPassword;
    private Button btnLogin;
    private TextView tvNoAccount;
    private ProgressBar progressBar;

    // Database and utilities
    private UserDatabase userDatabase;
    private ExecutorService executorService;
    private Handler mainHandler;

    // Security tracking
    private int loginAttempts = 0;
    private long lastFailedAttempt = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        Log.d(TAG, "LoginActivity created");

        // Load validation constants from resources
        loadValidationConstants();

        initializeComponents();
        setupEventListeners();
        setupAccessibility();

        Log.d(TAG, "LoginActivity initialization complete");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cleanupResources();
        Log.d(TAG, "LoginActivity destroyed");
    }

    /**
     * Load validation constants from resources
     */
    private void loadValidationConstants() {
        maxLoginAttempts = getResources().getInteger(R.integer.max_login_attempts);

        // Convert minutes to milliseconds
        int lockoutMinutes = getResources().getInteger(R.integer.lockout_duration_minutes);
        lockoutDurationMs = lockoutMinutes * 60 * 1000;

        maxInputLength = 1000; // Keep as constant or add to integers.xml later
    }

    /**
     * Initialize all UI components and supporting objects
     */
    private void initializeComponents() {
        try {
            // Initialize UI components
            etUsername = findViewById(R.id.etUsername);
            etPassword = findViewById(R.id.etPassword);
            btnLogin = findViewById(R.id.btnLogin);
            tvNoAccount = findViewById(R.id.tvNoAccount);
            progressBar = findViewById(R.id.progressBar);

            // Validate that all required components exist
            if (etUsername == null || etPassword == null || btnLogin == null || tvNoAccount == null) {
                Log.e(TAG, "One or more required UI components not found");
                showErrorAndFinish("Application error. Please restart the app.");
                return;
            }

            // Initialize supporting objects
            userDatabase = new UserDatabase(this);
            executorService = Executors.newSingleThreadExecutor();
            mainHandler = new Handler(Looper.getMainLooper());

            // Set initial UI state
            setLoadingState(false);

            Log.d(TAG, "Components initialized successfully");

        } catch (Exception e) {
            Log.e(TAG, "Error initializing components", e);
            showErrorAndFinish("Application initialization failed");
        }
    }

    /**
     * Setup event listeners for user interactions
     */
    private void setupEventListeners() {
        // Login button click listener
        btnLogin.setOnClickListener(this::handleLoginClick);

        // Password field Enter key listener
        etPassword.setOnEditorActionListener((textView, actionId, keyEvent) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                handleLoginClick(null);
                return true;
            }
            return false;
        });

        // Create account navigation
        tvNoAccount.setOnClickListener(this::handleCreateAccountClick);

        // Real-time input validation
        TextWatcher inputWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                clearFieldErrors();
                validateInputLength(s);
            }

            @Override
            public void afterTextChanged(Editable s) {}
        };

        etUsername.addTextChangedListener(inputWatcher);
        etPassword.addTextChangedListener(inputWatcher);

        Log.d(TAG, "Event listeners setup complete");
    }

    /**
     * Setup accessibility features for better usability
     */
    private void setupAccessibility() {
        etUsername.setContentDescription("Username input field");
        etPassword.setContentDescription("Password input field");
        btnLogin.setContentDescription("Login button");
        tvNoAccount.setContentDescription("Create new account link");

        // Set input types for security
        etPassword.setInputType(android.text.InputType.TYPE_CLASS_TEXT |
                android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
    }

    /**
     * Handle login button click with comprehensive validation and security measures
     *
     * @param view The clicked view (can be null for programmatic calls)
     */
    private void handleLoginClick(View view) {
        Log.d(TAG, "Login attempt initiated");

        try {
            // Check for rate limiting
            if (isLoginLocked()) {
                showToast(ERROR_TOO_MANY_ATTEMPTS);
                return;
            }

            // Get and validate input
            String username = getValidatedUsername();
            String password = getValidatedPassword();

            if (username == null || password == null) {
                return; // Error messages already shown by validation methods
            }

            // Perform login asynchronously
            performAsyncLogin(username, password);

        } catch (Exception e) {
            Log.e(TAG, "Unexpected error during login", e);
            showToast(ERROR_DATABASE_ERROR);
            setLoadingState(false);
        }
    }

    /**
     * Handle create account navigation
     *
     * @param view The clicked view
     */
    private void handleCreateAccountClick(View view) {
        try {
            Log.d(TAG, "Navigating to CreateAccountActivity");
            Intent intent = new Intent(this, CreateAccountActivity.class);
            startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Error navigating to create account", e);
            showToast("Unable to open account creation. Please try again.");
        }
    }

    /**
     * Check if login is currently locked due to too many failed attempts
     *
     * @return true if login is locked, false otherwise
     */
    private boolean isLoginLocked() {
        if (loginAttempts >= maxLoginAttempts) {
            long timeSinceLastFailure = System.currentTimeMillis() - lastFailedAttempt;
            if (timeSinceLastFailure < lockoutDurationMs) {
                Log.w(TAG, "Login blocked due to too many attempts");
                return true;
            } else {
                // Reset attempts after lockout period
                resetLoginAttempts();
            }
        }
        return false;
    }

    /**
     * Validate and retrieve username input
     *
     * @return Validated username or null if invalid
     */
    private String getValidatedUsername() {
        String username = etUsername.getText().toString().trim();

        if (username.isEmpty()) {
            etUsername.setError("Username is required");
            showToast(ERROR_EMPTY_FIELDS);
            etUsername.requestFocus();
            return null;
        }

        if (username.length() > maxInputLength) {
            etUsername.setError("Username too long");
            showToast(ERROR_INVALID_INPUT_LENGTH);
            return null;
        }

        return username;
    }

    /**
     * Validate and retrieve password input
     *
     * @return Validated password or null if invalid
     */
    private String getValidatedPassword() {
        String password = etPassword.getText().toString().trim();

        if (password.isEmpty()) {
            etPassword.setError("Password is required");
            showToast(ERROR_EMPTY_FIELDS);
            etPassword.requestFocus();
            return null;
        }

        if (password.length() > maxInputLength) {
            etPassword.setError("Password too long");
            showToast(ERROR_INVALID_INPUT_LENGTH);
            return null;
        }

        return password;
    }

    /**
     * Perform login validation asynchronously to prevent UI blocking
     *
     * @param username The username to validate
     * @param password The password to validate
     */
    private void performAsyncLogin(String username, String password) {
        setLoadingState(true);

        executorService.execute(() -> {
            boolean isValid = false;
            String userId = null;
            String errorMessage = null;

            try {
                long startTime = System.currentTimeMillis();

                // Validate user credentials
                isValid = userDatabase.validateUser(username, password);

                if (isValid) {
                    // Retrieve user ID
                    userId = userDatabase.getUserIdByUsername(username);
                    if (userId == null) {
                        isValid = false;
                        errorMessage = ERROR_USER_ID_RETRIEVAL;
                    }
                }

                // Implement minimum delay for security (prevent timing attacks)
                long elapsedTime = System.currentTimeMillis() - startTime;
                if (elapsedTime < LOGIN_DELAY_MS) {
                    try {
                        Thread.sleep(LOGIN_DELAY_MS - elapsedTime);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }

            } catch (IllegalArgumentException e) {
                Log.w(TAG, "Invalid login input: " + e.getMessage());
                errorMessage = "Please check your input and try again";
            } catch (SecurityException e) {
                Log.w(TAG, "Security error during login: " + e.getMessage());
                errorMessage = ERROR_INVALID_CREDENTIALS;
            } catch (Exception e) {
                Log.e(TAG, "Database error during login", e);
                errorMessage = ERROR_DATABASE_ERROR;
            }

            // Update UI on main thread
            final boolean finalIsValid = isValid;
            final String finalUserId = userId;
            final String finalErrorMessage = errorMessage;

            mainHandler.post(() -> handleLoginResult(finalIsValid, finalUserId, finalErrorMessage));
        });
    }

    /**
     * Handle the result of login validation
     *
     * @param isValid Whether the credentials were valid
     * @param userId The user ID if login was successful
     * @param errorMessage Error message if login failed
     */
    private void handleLoginResult(boolean isValid, String userId, String errorMessage) {
        setLoadingState(false);

        if (isValid && userId != null) {
            // Successful login
            resetLoginAttempts();
            showToast(SUCCESS_LOGIN);
            navigateToDashboard(userId);
        } else {
            // Failed login
            handleLoginFailure(errorMessage);
        }
    }

    /**
     * Handle login failure with appropriate security measures
     *
     * @param errorMessage The specific error message to show
     */
    private void handleLoginFailure(String errorMessage) {
        loginAttempts++;
        lastFailedAttempt = System.currentTimeMillis();

        String message = (errorMessage != null) ? errorMessage : ERROR_INVALID_CREDENTIALS;
        showToast(message);

        // Clear password field for security
        etPassword.setText("");
        etPassword.clearFocus();
        etUsername.requestFocus();

        Log.w(TAG, "Login failure #" + loginAttempts);

        if (loginAttempts >= maxLoginAttempts) {
            Log.w(TAG, "Maximum login attempts reached, locking login");
        }
    }

    /**
     * Navigate to dashboard with user ID
     *
     * @param userId The authenticated user's ID
     */
    private void navigateToDashboard(String userId) {
        try {
            Log.d(TAG, "Navigating to dashboard for user: " + userId);

            Intent intent = new Intent(this, DashboardActivity.class);
            intent.putExtra(EXTRA_USER_ID, userId);

            // Clear the back stack to prevent returning to login
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

            startActivity(intent);
            finish();

        } catch (Exception e) {
            Log.e(TAG, "Error navigating to dashboard", e);
            showToast("Login successful, but unable to open dashboard");
        }
    }

    /**
     * Set the loading state of the UI
     *
     * @param isLoading Whether the app is currently loading
     */
    private void setLoadingState(boolean isLoading) {
        if (progressBar != null) {
            progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        }

        if (btnLogin != null) {
            btnLogin.setEnabled(!isLoading);
            btnLogin.setText(isLoading ? "Signing In..." : "Login");
        }

        if (etUsername != null) {
            etUsername.setEnabled(!isLoading);
        }

        if (etPassword != null) {
            etPassword.setEnabled(!isLoading);
        }

        if (tvNoAccount != null) {
            tvNoAccount.setEnabled(!isLoading);
        }
    }

    /**
     * Clear field errors when user starts typing
     */
    private void clearFieldErrors() {
        if (etUsername != null) {
            etUsername.setError(null);
        }
        if (etPassword != null) {
            etPassword.setError(null);
        }
    }

    /**
     * Validate input length to prevent memory attacks
     *
     * @param input The input to validate
     */
    private void validateInputLength(CharSequence input) {
        if (input != null && input.length() > maxInputLength) {
            showToast("Input too long");
        }
    }

    /**
     * Reset login attempt tracking
     */
    private void resetLoginAttempts() {
        loginAttempts = 0;
        lastFailedAttempt = 0;
    }

    /**
     * Show a toast message safely
     *
     * @param message The message to show
     */
    private void showToast(String message) {
        if (message != null && !isFinishing()) {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Show error message and finish activity
     *
     * @param message The error message to show
     */
    private void showErrorAndFinish(String message) {
        showToast(message);
        Log.e(TAG, "Fatal error, finishing activity: " + message);
        finish();
    }

    /**
     * Clean up resources when activity is destroyed
     */
    private void cleanupResources() {
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
        }

        if (userDatabase != null) {
            try {
                userDatabase.close();
            } catch (Exception e) {
                Log.e(TAG, "Error closing database", e);
            }
        }
    }
}