package com.shaynesullivan.stockshark;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.telephony.SmsManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import com.google.android.material.appbar.MaterialToolbar;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

/**
 * SmsNotificationsActivity handles SMS notification setup.
 */
public class SmsNotificationsActivity extends AppCompatActivity {

    // Permission constants
    private static final int SMS_PERMISSION_REQUEST_CODE = 101;
    private static final String[] REQUIRED_PERMISSIONS = {Manifest.permission.SEND_SMS};

    // Validation constants - loaded from resources
    private int minPhoneLength;
    private int maxPhoneLength;
    private static final int MAX_SMS_LENGTH = 160; // Standard SMS length

    // Phone number validation patterns
    private static final Pattern PHONE_PATTERN_US_DASHED = Pattern.compile("^\\d{3}-\\d{3}-\\d{4}$");
    private static final Pattern PHONE_PATTERN_US_PLAIN = Pattern.compile("^\\d{10}$");
    private static final Pattern PHONE_PATTERN_INTERNATIONAL = Pattern.compile("^\\+?[1-9]\\d{1,14}$");

    // SMS content constants
    private static final String SMS_TEST_MESSAGE = "Stock Shark: SMS alerts successfully enabled! You'll receive notifications when inventory runs low.";
    private static final String SMS_SENDER_ID = null; // Use default

    // Error messages
    private static final String ERROR_EMPTY_PHONE = "Please enter a phone number";
    private static final String ERROR_INVALID_PHONE = "Please enter a valid phone number (e.g., 123-456-7890 or 1234567890)";
    private String errorPhoneTooLong; // Will be built dynamically
    private static final String ERROR_SMS_SEND_FAILED = "Failed to send test SMS. Please try again.";
    private static final String ERROR_SMS_PERMISSION_DENIED = "SMS permission is required to send notifications";
    private static final String ERROR_SMS_NOT_SUPPORTED = "SMS functionality is not available on this device";

    // Success messages
    private static final String SUCCESS_SMS_SENT = "Test SMS sent successfully! SMS notifications are now enabled.";
    private static final String SUCCESS_PERMISSION_GRANTED = "SMS permission granted";

    // Logging tag (max 23 characters for Android)
    private static final String TAG = "SmsNotificationsAct";

    // UI components
    private EditText etPhoneNumber;
    private Button btnSendSms;
    private ImageButton btnBack;
    private ProgressBar progressBar;

    // Utilities
    private ExecutorService executorService;
    private Handler mainHandler;

    // State tracking
    private boolean isProcessing = false;
    private String validatedPhoneNumber = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sms_notifications);

        // Set the toolbar as the support action bar
        MaterialToolbar toolbar = findViewById(R.id.topAppBar);
        setSupportActionBar(toolbar);

        // Enable back button in toolbar
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }

        Log.d(TAG, "SmsNotificationsActivity created");

        loadValidationConstants();

        initializeComponents();
        setupEventListeners();
        setupAccessibility();
        checkSmsCapability();

        Log.d(TAG, "SmsNotificationsActivity initialization complete");
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cleanupResources();
        Log.d(TAG, "SmsNotificationsActivity destroyed");
    }

    /**
     * Load validation constants from resources
     */
    private void loadValidationConstants() {
        minPhoneLength = getResources().getInteger(R.integer.min_phone_length);
        maxPhoneLength = getResources().getInteger(R.integer.max_phone_length);

        // Build dynamic error message
        errorPhoneTooLong = "Phone number is too long (max " + maxPhoneLength + " digits)";
    }

    /**
     * Initialize all UI components and supporting objects
     */
    private void initializeComponents() {
        try {
            // Initialize UI components
            etPhoneNumber = findViewById(R.id.etPhoneNumber);
            btnSendSms = findViewById(R.id.btnSendSms);
            btnBack = findViewById(R.id.btnBack);
            progressBar = findViewById(R.id.progressBar);

            // Validate required components exist
            if (etPhoneNumber == null || btnSendSms == null || btnBack == null) {
                Log.e(TAG, "One or more required UI components not found");
                showErrorAndFinish("Application error. Please restart the app.");
                return;
            }

            // Initialize supporting objects
            executorService = Executors.newSingleThreadExecutor();
            mainHandler = new Handler(Looper.getMainLooper());

            // Set initial UI state
            setProcessingState(false);

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
        // Send SMS button click listener
        btnSendSms.setOnClickListener(this::handleSendSmsClick);

        // Back button click listener
        btnBack.setOnClickListener(this::handleBackClick);

        // Real-time phone number validation
        etPhoneNumber.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                clearFieldErrors();
                validatePhoneNumberRealTime(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        Log.d(TAG, "Event listeners setup complete");
    }

    /**
     * Setup accessibility features for better usability
     */
    private void setupAccessibility() {
        etPhoneNumber.setContentDescription("Phone number input field for SMS notifications");
        btnSendSms.setContentDescription("Send test SMS button");
        btnBack.setContentDescription("Go back to previous screen");

        // Set appropriate input type
        etPhoneNumber.setInputType(android.text.InputType.TYPE_CLASS_PHONE);
    }

    /**
     * Check if SMS functionality is supported on this device
     */
    private void checkSmsCapability() {
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) {
            Log.w(TAG, "Device does not support telephony features");
            showUnsupportedDeviceDialog();
        }
    }

    /**
     * Handle send SMS button click with comprehensive validation
     *
     * @param view The clicked view
     */
    private void handleSendSmsClick(View view) {
        Log.d(TAG, "SMS send attempt initiated");

        if (isProcessing) {
            Log.d(TAG, "SMS operation already in progress");
            return;
        }

        try {
            // Validate phone number input
            String phoneNumber = validatePhoneNumberInput();
            if (phoneNumber == null) {
                return; // Error already shown by validation method
            }

            validatedPhoneNumber = phoneNumber;

            // Check and request SMS permission
            checkAndRequestSmsPermission();

        } catch (Exception e) {
            Log.e(TAG, "Unexpected error during SMS send", e);
            showToast(ERROR_SMS_SEND_FAILED);
            setProcessingState(false);
        }
    }

    /**
     * Handle back button click
     *
     * @param view The clicked view
     */
    private void handleBackClick(View view) {
        try {
            Log.d(TAG, "Back button pressed");

            if (isProcessing) {
                // Show confirmation dialog if operation is in progress
                showCancelOperationDialog();
            } else {
                finish();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error handling back button", e);
            finish(); // Always try to close the activity
        }
    }

    /**
     * Validate phone number input with comprehensive checks
     *
     * @return Validated phone number or null if invalid
     */
    private String validatePhoneNumberInput() {
        String phoneNumber = etPhoneNumber.getText().toString().trim();

        // Check for empty input
        if (phoneNumber.isEmpty()) {
            etPhoneNumber.setError("Phone number is required");
            showToast(ERROR_EMPTY_PHONE);
            etPhoneNumber.requestFocus();
            return null;
        }

        // Check length constraints
        if (phoneNumber.length() > maxPhoneLength) {
            etPhoneNumber.setError("Phone number too long");
            showToast(errorPhoneTooLong);
            return null;
        }

        if (phoneNumber.length() < minPhoneLength) {
            etPhoneNumber.setError("Phone number too short");
            showToast(ERROR_INVALID_PHONE);
            return null;
        }

        // Validate format using multiple patterns
        if (!isValidPhoneNumber(phoneNumber)) {
            etPhoneNumber.setError("Invalid format");
            showToast(ERROR_INVALID_PHONE);
            return null;
        }

        // Sanitize phone number (remove formatting)
        return sanitizePhoneNumber(phoneNumber);
    }

    /**
     * Validate phone number format using multiple patterns
     *
     * @param phoneNumber The phone number to validate
     * @return true if valid, false otherwise
     */
    private boolean isValidPhoneNumber(String phoneNumber) {
        return PHONE_PATTERN_US_DASHED.matcher(phoneNumber).matches() ||
                PHONE_PATTERN_US_PLAIN.matcher(phoneNumber).matches() ||
                PHONE_PATTERN_INTERNATIONAL.matcher(phoneNumber).matches();
    }

    /**
     * Sanitize phone number by removing formatting characters
     *
     * @param phoneNumber The phone number to sanitize
     * @return Sanitized phone number
     */
    private String sanitizePhoneNumber(String phoneNumber) {
        // Remove common formatting characters but preserve + for international numbers
        return phoneNumber.replaceAll("[\\s\\-\\(\\)\\.]", "");
    }

    /**
     * Real-time phone number validation feedback
     *
     * @param phoneNumber The current phone number input
     */
    private void validatePhoneNumberRealTime(String phoneNumber) {
        if (phoneNumber.length() > maxPhoneLength) {
            etPhoneNumber.setError("Too long");
        }
    }

    /**
     * Check SMS permission and request if necessary
     */
    private void checkAndRequestSmsPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS)
                == PackageManager.PERMISSION_GRANTED) {
            // Permission already granted
            Log.d(TAG, "SMS permission already granted");
            sendSmsAsync(validatedPhoneNumber);
        } else {
            // Need to request permission
            Log.d(TAG, "Requesting SMS permission");
            requestSmsPermission();
        }
    }

    /**
     * Request SMS permission with user education
     */
    private void requestSmsPermission() {
        if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.SEND_SMS)) {
            // Show explanation dialog
            showPermissionRationaleDialog();
        } else {
            // Request permission directly
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, SMS_PERMISSION_REQUEST_CODE);
        }
    }

    /**
     * Send SMS asynchronously to prevent UI blocking
     *
     * @param phoneNumber The validated phone number
     */
    private void sendSmsAsync(String phoneNumber) {
        setProcessingState(true);

        executorService.execute(() -> {
            boolean success = false;
            String errorMessage = null;

            try {
                Log.d(TAG, "Attempting to send SMS to: " + maskPhoneNumber(phoneNumber));

                SmsManager smsManager = SmsManager.getDefault();

                // Check if message needs to be split (for long messages)
                if (SMS_TEST_MESSAGE.length() <= MAX_SMS_LENGTH) {
                    smsManager.sendTextMessage(phoneNumber, SMS_SENDER_ID, SMS_TEST_MESSAGE, null, null);
                } else {
                    // Split long message
                    java.util.ArrayList<String> parts = smsManager.divideMessage(SMS_TEST_MESSAGE);
                    smsManager.sendMultipartTextMessage(phoneNumber, SMS_SENDER_ID, parts, null, null);
                }

                success = true;
                Log.d(TAG, "SMS sent successfully");

            } catch (SecurityException e) {
                Log.e(TAG, "Security exception sending SMS", e);
                errorMessage = ERROR_SMS_PERMISSION_DENIED;
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "Invalid SMS parameters", e);
                errorMessage = ERROR_INVALID_PHONE;
            } catch (Exception e) {
                Log.e(TAG, "Failed to send SMS", e);
                errorMessage = ERROR_SMS_SEND_FAILED;
            }

            // Update UI on main thread
            final boolean finalSuccess = success;
            final String finalErrorMessage = errorMessage;

            mainHandler.post(() -> handleSmsResult(finalSuccess, finalErrorMessage));
        });
    }

    /**
     * Handle the result of SMS sending operation
     *
     * @param success Whether SMS was sent successfully
     * @param errorMessage Error message if operation failed
     */
    private void handleSmsResult(boolean success, String errorMessage) {
        setProcessingState(false);

        if (success) {
            showToast(SUCCESS_SMS_SENT);
            Log.d(TAG, "SMS notification setup completed successfully");

            // Navigate back after successful setup
            mainHandler.postDelayed(this::finish, 2000); // 2 second delay to show success message
        } else {
            String message = (errorMessage != null) ? errorMessage : ERROR_SMS_SEND_FAILED;
            showToast(message);
            Log.w(TAG, "SMS setup failed: " + message);
        }
    }

    /**
     * Handle permission request results
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == SMS_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, SUCCESS_PERMISSION_GRANTED);
                showToast(SUCCESS_PERMISSION_GRANTED);

                if (validatedPhoneNumber != null) {
                    sendSmsAsync(validatedPhoneNumber);
                }
            } else {
                Log.w(TAG, "SMS permission denied");
                handlePermissionDenied();
            }
        }
    }

    /**
     * Handle permission denial with user guidance
     */
    private void handlePermissionDenied() {
        showToast(ERROR_SMS_PERMISSION_DENIED);

        // Check if user selected "Don't ask again"
        if (!ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.SEND_SMS)) {
            showPermissionDeniedDialog();
        }
    }

    /**
     * Set the processing state of the UI
     *
     * @param isProcessing Whether the app is currently processing
     */
    private void setProcessingState(boolean isProcessing) {
        this.isProcessing = isProcessing;

        if (progressBar != null) {
            progressBar.setVisibility(isProcessing ? View.VISIBLE : View.GONE);
        }

        if (btnSendSms != null) {
            btnSendSms.setEnabled(!isProcessing);
            btnSendSms.setText(isProcessing ? "Sending..." : "Send Test SMS");
        }

        if (etPhoneNumber != null) {
            etPhoneNumber.setEnabled(!isProcessing);
        }

        if (btnBack != null) {
            btnBack.setEnabled(!isProcessing);
        }
    }

    /**
     * Clear field errors when user starts typing
     */
    private void clearFieldErrors() {
        if (etPhoneNumber != null) {
            etPhoneNumber.setError(null);
        }
    }

    /**
     * Show permission rationale dialog
     */
    private void showPermissionRationaleDialog() {
        new AlertDialog.Builder(this)
                .setTitle("SMS Permission Required")
                .setMessage("Stock Shark needs SMS permission to send you low inventory alerts. " +
                        "This helps you stay informed about your stock levels.")
                .setPositiveButton("Grant Permission", (dialog, which) -> {
                    ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, SMS_PERMISSION_REQUEST_CODE);
                })
                .setNegativeButton("Cancel", (dialog, which) -> {
                    showToast("SMS notifications will not be available without permission");
                })
                .setCancelable(false)
                .show();
    }

    /**
     * Show permission permanently denied dialog
     */
    private void showPermissionDeniedDialog() {
        new AlertDialog.Builder(this)
                .setTitle("SMS Permission Denied")
                .setMessage("SMS notifications are disabled. To enable them, go to Settings > Apps > Stock Shark > Permissions and enable SMS permission.")
                .setPositiveButton("OK", null)
                .show();
    }

    /**
     * Show unsupported device dialog
     */
    private void showUnsupportedDeviceDialog() {
        new AlertDialog.Builder(this)
                .setTitle("SMS Not Supported")
                .setMessage(ERROR_SMS_NOT_SUPPORTED)
                .setPositiveButton("OK", (dialog, which) -> finish())
                .setCancelable(false)
                .show();
    }

    /**
     * Show cancel operation dialog
     */
    private void showCancelOperationDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Cancel SMS Setup")
                .setMessage("SMS setup is in progress. Are you sure you want to cancel?")
                .setPositiveButton("Yes, Cancel", (dialog, which) -> {
                    Log.d(TAG, "User cancelled SMS operation");
                    finish();
                })
                .setNegativeButton("Continue", null)
                .show();
    }

    /**
     * Mask phone number for logging (privacy protection)
     *
     * @param phoneNumber The phone number to mask
     * @return Masked phone number
     */
    private String maskPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.length() < 4) {
            return "****";
        }
        return "****" + phoneNumber.substring(phoneNumber.length() - 4);
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
    }
}