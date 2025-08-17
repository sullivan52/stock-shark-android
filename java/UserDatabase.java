package com.shaynesullivan.stockshark;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.regex.Pattern;

/**
 * UserDatabase manages SQLite database operations for user authentication.
 */
public class UserDatabase extends SQLiteOpenHelper {

    // Database configuration constants
    private static final String DATABASE_NAME = "user.db";
    private static final int DATABASE_VERSION = 3; // Incremented for security improvements

    // Table and column name constants
    private static final String TABLE_USERS = "users";
    private static final String COL_ID = "_id";
    private static final String COL_USERNAME = "username";
    private static final String COL_PASSWORD_HASH = "password_hash";
    private static final String COL_SALT = "salt";
    private static final String COL_CREATED_AT = "created_at";

    // Security constants
    private static final String HASH_ALGORITHM = "SHA-256";
    private static final int SALT_LENGTH = 32; // 256 bits
    private static final String CHARSET_UTF8 = "UTF-8";

    // Validation constants - loaded from resources
    private final int minUsernameLength;
    private final int maxUsernameLength;
    private final int minPasswordLength;
    private final int maxPasswordLength;

    // Dynamic error messages
    private final String errorUsernameLength;
    private final String errorPasswordLength;

    // Username validation pattern: alphanumeric and underscores only
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_]+$");

    // SQL statement template - will use actual values when created
    private final String createTableSql;

    private static final String DROP_TABLE_SQL = "DROP TABLE IF EXISTS " + TABLE_USERS;

    private static final String CREATE_USERNAME_INDEX_SQL =
            "CREATE UNIQUE INDEX IF NOT EXISTS idx_username ON " + TABLE_USERS + "(" + COL_USERNAME + ")";

    // Static error messages
    private static final String ERROR_INVALID_USERNAME = "Username cannot be null or empty";
    private static final String ERROR_INVALID_PASSWORD = "Password cannot be null or empty";
    private static final String ERROR_USERNAME_INVALID_CHARS = "Username can only contain letters, numbers, and underscores";
    private static final String ERROR_USERNAME_EXISTS = "Username already exists";
    private static final String ERROR_DATABASE_OPERATION = "Database operation failed";
    private static final String ERROR_HASH_GENERATION = "Failed to generate password hash";

    // Logging tag
    private static final String TAG = "UserDatabase";

    // Secure random instance for salt generation
    private final SecureRandom secureRandom;

    /**
     * Constructor for UserDatabase
     *
     * @param context Application context for database operations
     * @throws IllegalArgumentException if context is null
     */
    public UserDatabase(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);

        if (context == null) {
            throw new IllegalArgumentException("Context cannot be null");
        }

        // Load validation constants from resources
        minUsernameLength = context.getResources().getInteger(R.integer.min_username_length);
        maxUsernameLength = context.getResources().getInteger(R.integer.max_username_length);
        minPasswordLength = context.getResources().getInteger(R.integer.min_password_length);
        maxPasswordLength = context.getResources().getInteger(R.integer.max_password_length);

        // Build dynamic error messages
        errorUsernameLength = "Username must be " + minUsernameLength + "-" + maxUsernameLength + " characters";
        errorPasswordLength = "Password must be " + minPasswordLength + "-" + maxPasswordLength + " characters";

        // Build SQL statement with actual values
        createTableSql = "CREATE TABLE " + TABLE_USERS + " (" +
                COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COL_USERNAME + " TEXT NOT NULL UNIQUE, " +
                COL_PASSWORD_HASH + " TEXT NOT NULL, " +
                COL_SALT + " TEXT NOT NULL, " +
                COL_CREATED_AT + " INTEGER NOT NULL DEFAULT (strftime('%s', 'now')), " +
                "CHECK(length(" + COL_USERNAME + ") >= " + minUsernameLength + "), " +
                "CHECK(length(" + COL_USERNAME + ") <= " + maxUsernameLength + "), " +
                "CHECK(length(" + COL_PASSWORD_HASH + ") > 0), " +
                "CHECK(length(" + COL_SALT + ") > 0))";

        this.secureRandom = new SecureRandom();
    }

    /**
     * Called when the database is created for the first time.
     * Creates the users table with proper constraints and indexes.
     *
     * @param db The database instance
     */
    @Override
    public void onCreate(SQLiteDatabase db) {
        try {
            Log.d(TAG, "Creating users database table");
            db.execSQL(createTableSql);
            db.execSQL(CREATE_USERNAME_INDEX_SQL);
            Log.i(TAG, "Users database table created successfully");
        } catch (SQLException e) {
            Log.e(TAG, "Failed to create users database table", e);
            throw new SQLiteException("Failed to create users table: " + e.getMessage());
        }
    }

    /**
     * Called when the database needs to be upgraded.
     * Handles migration from older versions to preserve existing user data.
     *
     * @param db The database instance
     * @param oldVersion The old database version
     * @param newVersion The new database version
     */
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        Log.w(TAG, "Upgrading users database from version " + oldVersion + " to " + newVersion);

        try {
            if (oldVersion < 3) {
                // Migration from version 2 to 3: Add security improvements
                migrateToSecurePasswords(db);
            }
            Log.i(TAG, "Users database upgraded successfully");
        } catch (SQLException e) {
            Log.e(TAG, "Failed to upgrade users database", e);
            // Fallback: recreate table
            Log.w(TAG, "Falling back to table recreation due to migration failure");
            db.execSQL(DROP_TABLE_SQL);
            onCreate(db);
        }
    }

    /**
     * Add a new user to the database with secure password hashing
     *
     * @param username The username for the new user
     * @param password The plaintext password (will be hashed)
     * @return The ID of the newly created user, or -1 if the operation failed
     * @throws IllegalArgumentException if input validation fails
     * @throws SecurityException if username already exists
     */
    public long addUser(String username, String password) {
        // Input validation
        validateUsername(username);
        validatePassword(password);

        // Check if username already exists
        if (userExists(username)) {
            throw new SecurityException(ERROR_USERNAME_EXISTS);
        }

        SQLiteDatabase db = null;
        long result = -1;

        try {
            db = getWritableDatabase();
            db.beginTransaction();

            // Generate secure salt and hash password
            String salt = generateSalt();
            String passwordHash = hashPassword(password, salt);

            ContentValues values = new ContentValues();
            values.put(COL_USERNAME, username.trim().toLowerCase()); // Normalize username
            values.put(COL_PASSWORD_HASH, passwordHash);
            values.put(COL_SALT, salt);

            result = db.insert(TABLE_USERS, null, values);

            if (result != -1) {
                db.setTransactionSuccessful();
                Log.d(TAG, "Successfully added user: " + username);
            } else {
                Log.w(TAG, "Failed to insert user: " + username);
            }

        } catch (SQLException e) {
            Log.e(TAG, "Database error adding user: " + username, e);
            result = -1;
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error adding user: " + username, e);
            result = -1;
        } finally {
            if (db != null) {
                try {
                    db.endTransaction();
                } catch (SQLException e) {
                    Log.e(TAG, "Error ending transaction", e);
                }
            }
        }

        return result;
    }

    /**
     * Validate user credentials against stored hashed passwords
     *
     * @param username The username to validate
     * @param password The plaintext password to validate
     * @return true if credentials are valid, false otherwise
     * @throws IllegalArgumentException if input validation fails
     */
    public boolean validateUser(String username, String password) {
        validateUsername(username);
        validatePassword(password);

        SQLiteDatabase db = null;
        Cursor cursor = null;
        boolean isValid = false;

        try {
            db = getReadableDatabase();
            cursor = db.rawQuery(
                    "SELECT " + COL_PASSWORD_HASH + ", " + COL_SALT +
                            " FROM " + TABLE_USERS +
                            " WHERE " + COL_USERNAME + " = ?",
                    new String[]{username.trim().toLowerCase()}
            );

            if (cursor.moveToFirst()) {
                String storedHash = cursor.getString(0);
                String salt = cursor.getString(1);

                String inputHash = hashPassword(password, salt);
                isValid = storedHash.equals(inputHash);

                Log.d(TAG, "User validation " + (isValid ? "successful" : "failed") + " for: " + username);
            } else {
                Log.d(TAG, "User not found during validation: " + username);
            }

        } catch (SQLException e) {
            Log.e(TAG, "Database error validating user: " + username, e);
            isValid = false;
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error validating user: " + username, e);
            isValid = false;
        } finally {
            closeCursor(cursor);
        }

        return isValid;
    }

    /**
     * Retrieve the user ID for a given username
     *
     * @param username The username to look up
     * @return The user ID as a string, or null if user doesn't exist or on error
     * @throws IllegalArgumentException if username is invalid
     */
    public String getUserIdByUsername(String username) {
        validateUsername(username);

        SQLiteDatabase db = null;
        Cursor cursor = null;
        String userId = null;

        try {
            db = getReadableDatabase();
            cursor = db.rawQuery(
                    "SELECT " + COL_ID +
                            " FROM " + TABLE_USERS +
                            " WHERE " + COL_USERNAME + " = ?",
                    new String[]{username.trim().toLowerCase()}
            );

            if (cursor.moveToFirst()) {
                userId = String.valueOf(cursor.getLong(0));
                Log.d(TAG, "Retrieved user ID for: " + username);
            } else {
                Log.d(TAG, "User not found for ID lookup: " + username);
            }

        } catch (SQLException e) {
            Log.e(TAG, "Database error getting user ID for: " + username, e);
            userId = null;
        } finally {
            closeCursor(cursor);
        }

        return userId;
    }

    /**
     * Check if a username already exists in the database
     *
     * @param username The username to check
     * @return true if the username exists, false otherwise
     * @throws IllegalArgumentException if username is invalid
     */
    public boolean userExists(String username) {
        validateUsername(username);

        SQLiteDatabase db = null;
        Cursor cursor = null;
        boolean exists = false;

        try {
            db = getReadableDatabase();
            cursor = db.rawQuery(
                    "SELECT 1 FROM " + TABLE_USERS +
                            " WHERE " + COL_USERNAME + " = ?",
                    new String[]{username.trim().toLowerCase()}
            );

            exists = cursor.moveToFirst();

        } catch (SQLException e) {
            Log.e(TAG, "Database error checking if user exists: " + username, e);
            exists = false;
        } finally {
            closeCursor(cursor);
        }

        return exists;
    }

    /**
     * Get the total number of registered users
     *
     * @return The number of users, or -1 on error
     */
    public int getUserCount() {
        SQLiteDatabase db = null;
        Cursor cursor = null;
        int count = -1;

        try {
            db = getReadableDatabase();
            cursor = db.rawQuery("SELECT COUNT(*) FROM " + TABLE_USERS, null);

            if (cursor.moveToFirst()) {
                count = cursor.getInt(0);
            }

        } catch (SQLException e) {
            Log.e(TAG, "Database error getting user count", e);
            count = -1;
        } finally {
            closeCursor(cursor);
        }

        return count;
    }

    // Private helper methods

    /**
     * Migrate existing users from plaintext passwords to hashed passwords
     * This method handles the upgrade from version 2 to 3
     *
     * @param db The database instance
     */
    private void migrateToSecurePasswords(SQLiteDatabase db) throws SQLException {
        Log.i(TAG, "Migrating to secure password storage");

        // For security reasons, we cannot migrate existing plaintext passwords
        // Users will need to create new accounts with secure password storage
        db.execSQL(DROP_TABLE_SQL);
        onCreate(db);

        Log.w(TAG, "Password migration completed - existing users must re-register for security");
    }

    /**
     * Validate username according to business rules
     *
     * @param username The username to validate
     * @throws IllegalArgumentException if validation fails
     */
    private void validateUsername(String username) {
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException(ERROR_INVALID_USERNAME);
        }

        String trimmedUsername = username.trim();

        if (trimmedUsername.length() < minUsernameLength || trimmedUsername.length() > maxUsernameLength) {
            throw new IllegalArgumentException(errorUsernameLength);
        }

        if (!USERNAME_PATTERN.matcher(trimmedUsername).matches()) {
            throw new IllegalArgumentException(ERROR_USERNAME_INVALID_CHARS);
        }
    }

    /**
     * Validate password according to security requirements
     *
     * @param password The password to validate
     * @throws IllegalArgumentException if validation fails
     */
    private void validatePassword(String password) {
        if (password == null || password.isEmpty()) {
            throw new IllegalArgumentException(ERROR_INVALID_PASSWORD);
        }

        if (password.length() < minPasswordLength || password.length() > maxPasswordLength) {
            throw new IllegalArgumentException(errorPasswordLength);
        }
    }

    /**
     * Generate a cryptographically secure random salt
     *
     * @return A hexadecimal string representation of the salt
     */
    private String generateSalt() {
        byte[] salt = new byte[SALT_LENGTH];
        secureRandom.nextBytes(salt);
        return bytesToHex(salt);
    }

    /**
     * Hash a password with the provided salt using SHA-256
     *
     * @param password The plaintext password
     * @param salt The salt to use for hashing
     * @return The hexadecimal string representation of the hash
     * @throws SecurityException if hashing fails
     */
    private String hashPassword(String password, String salt) {
        try {
            MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);
            digest.update(salt.getBytes(CHARSET_UTF8));
            byte[] hashedBytes = digest.digest(password.getBytes(CHARSET_UTF8));
            return bytesToHex(hashedBytes);
        } catch (NoSuchAlgorithmException | java.io.UnsupportedEncodingException e) {
            Log.e(TAG, "Failed to hash password", e);
            throw new SecurityException(ERROR_HASH_GENERATION + ": " + e.getMessage());
        }
    }

    /**
     * Convert byte array to hexadecimal string
     *
     * @param bytes The byte array to convert
     * @return Hexadecimal string representation
     */
    private String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }

    /**
     * Safely close a database cursor
     *
     * @param cursor The cursor to close
     */
    private void closeCursor(Cursor cursor) {
        if (cursor != null && !cursor.isClosed()) {
            try {
                cursor.close();
            } catch (Exception e) {
                Log.e(TAG, "Error closing cursor", e);
            }
        }
    }
}