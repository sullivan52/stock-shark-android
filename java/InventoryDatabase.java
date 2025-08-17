package com.shaynesullivan.stockshark;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * InventoryDatabase manages SQLite database operations for inventory items.
 * The database stores inventory items with user association, allowing multiple users
 * to maintain separate inventory lists within the same application.
 */
public class InventoryDatabase extends SQLiteOpenHelper {

    // Database configuration constants
    private static final String DATABASE_NAME = "inventory.db";
    private static final int DATABASE_VERSION = 2;

    // Table and column name constants
    private static final String TABLE_ITEMS = "items";
    private static final String COL_ID = "_id";
    private static final String COL_NAME = "name";
    private static final String COL_QUANTITY = "quantity";
    private static final String COL_USER_ID = "user_id";

    // Validation constants - loaded from resources
    private final int maxNameLength;
    private final int minQuantity;
    private final int maxQuantity;
    private static final int MAX_USER_ID_LENGTH = 100;

    // Dynamic error messages
    private final String errorNameTooLong;
    private final String errorInvalidQuantity;

    // SQL statement constants
    private static final String CREATE_TABLE_SQL_TEMPLATE =
            "CREATE TABLE " + TABLE_ITEMS + " (" +
                    COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    COL_NAME + " TEXT NOT NULL, " +
                    COL_QUANTITY + " INTEGER NOT NULL DEFAULT 0, " +
                    COL_USER_ID + " TEXT NOT NULL, " +
                    "CHECK(" + COL_QUANTITY + " >= 0), " +
                    "CHECK(length(" + COL_NAME + ") > 0), " +
                    "CHECK(length(" + COL_USER_ID + ") > 0))";

    private static final String DROP_TABLE_SQL = "DROP TABLE IF EXISTS " + TABLE_ITEMS;

    private static final String SELECT_BY_USER_SQL =
            "SELECT " + COL_ID + ", " + COL_NAME + ", " + COL_QUANTITY + ", " + COL_USER_ID +
                    " FROM " + TABLE_ITEMS + " WHERE " + COL_USER_ID + " = ? ORDER BY " + COL_NAME + " ASC";

    // Static error messages
    private static final String ERROR_INVALID_ITEM = "Item cannot be null";
    private static final String ERROR_INVALID_USER_ID = "User ID cannot be null or empty";
    private static final String ERROR_INVALID_NAME = "Item name cannot be null or empty";
    private static final String ERROR_USER_ID_TOO_LONG = "User ID cannot exceed " + MAX_USER_ID_LENGTH + " characters";
    private static final String ERROR_DATABASE_OPERATION = "Database operation failed";

    // Logging tag
    private static final String TAG = "InventoryDatabase";

    /**
     * Constructor for InventoryDatabase
     *
     * @param context Application context for database operations
     * @throws IllegalArgumentException if context is null
     */
    public InventoryDatabase(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);

        if (context == null) {
            throw new IllegalArgumentException("Context cannot be null");
        }

        // Load validation constants from resources
        maxNameLength = context.getResources().getInteger(R.integer.max_item_name_length);
        minQuantity = context.getResources().getInteger(R.integer.min_quantity);
        maxQuantity = context.getResources().getInteger(R.integer.max_quantity);

        // Build dynamic error messages
        errorNameTooLong = "Item name cannot exceed " + maxNameLength + " characters";
        errorInvalidQuantity = "Quantity must be between " + minQuantity + " and " + maxQuantity;
    }

    /**
     * Called when the database is created for the first time.
     * Creates the inventory table with proper constraints and indexes.
     *
     * @param db The database instance
     */
    @Override
    public void onCreate(SQLiteDatabase db) {
        try {
            Log.d(TAG, "Creating inventory database table");
            db.execSQL(CREATE_TABLE_SQL_TEMPLATE);

            // Create index on user_id for faster queries
            createUserIdIndex(db);

            Log.i(TAG, "Database table created successfully");
        } catch (SQLException e) {
            Log.e(TAG, "Failed to create database table", e);
            throw new SQLiteException("Failed to create inventory table: " + e.getMessage());
        }
    }

    /**
     * Called when the database needs to be upgraded.
     * Currently drops and recreates the table (data loss approach).
     * In the future, this should use ALTER TABLE statements for data preservation.
     *
     * @param db The database instance
     * @param oldVersion The old database version
     * @param newVersion The new database version
     */
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        Log.w(TAG, "Upgrading database from version " + oldVersion + " to " + newVersion);

        try {
            db.execSQL(DROP_TABLE_SQL);
            onCreate(db);
            Log.i(TAG, "Database upgraded successfully");
        } catch (SQLException e) {
            Log.e(TAG, "Failed to upgrade database", e);
            throw new SQLiteException("Failed to upgrade database: " + e.getMessage());
        }
    }

    /**
     * Add a new inventory item to the database with comprehensive validation
     *
     * @param item The inventory item to add
     * @return The ID of the newly inserted item, or -1 if the operation failed
     * @throws IllegalArgumentException if item validation fails
     */
    public long addItem(InventoryItem item) {
        // Input validation
        validateInventoryItem(item);

        SQLiteDatabase db = null;
        long result = -1;

        try {
            db = getWritableDatabase();
            db.beginTransaction();

            ContentValues values = createContentValues(item);
            result = db.insert(TABLE_ITEMS, null, values);

            if (result != -1) {
                db.setTransactionSuccessful();
                Log.d(TAG, "Successfully added item: " + item.getName() + " for user: " + item.getUserId());
            } else {
                Log.w(TAG, "Failed to insert item: " + item.getName());
            }

        } catch (SQLException e) {
            Log.e(TAG, "Database error adding item: " + item.getName(), e);
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
     * Retrieve all inventory items for a specific user with proper error handling
     *
     * @param userId The user ID to filter by
     * @return List of inventory items for the user, empty list if none found or on error
     * @throws IllegalArgumentException if userId is invalid
     */
    public List<InventoryItem> getItemsByUser(String userId) {
        validateUserId(userId);

        List<InventoryItem> items = new ArrayList<>();
        SQLiteDatabase db = null;
        Cursor cursor = null;

        try {
            db = getReadableDatabase();
            cursor = db.rawQuery(SELECT_BY_USER_SQL, new String[]{userId});

            items = extractItemsFromCursor(cursor);
            Log.d(TAG, "Retrieved " + items.size() + " items for user: " + userId);

        } catch (SQLException e) {
            Log.e(TAG, "Database error retrieving items for user: " + userId, e);
            // Return empty list instead of null to prevent NPE in calling code
        } finally {
            closeCursor(cursor);
        }

        return items;
    }

    /**
     * Update an existing inventory item with validation and error handling
     *
     * @param item The inventory item to update
     * @return true if the update was successful, false otherwise
     * @throws IllegalArgumentException if item validation fails
     */
    public boolean updateItem(InventoryItem item) {
        validateInventoryItem(item);

        if (item.getId() <= 0) {
            throw new IllegalArgumentException("Item ID must be positive");
        }

        SQLiteDatabase db = null;
        boolean success = false;

        try {
            db = getWritableDatabase();
            db.beginTransaction();

            ContentValues values = new ContentValues();
            values.put(COL_NAME, item.getName());
            values.put(COL_QUANTITY, item.getQuantity());
            // Note: Don't update user_id as it should remain constant

            int rowsAffected = db.update(
                    TABLE_ITEMS,
                    values,
                    COL_ID + " = ? AND " + COL_USER_ID + " = ?",
                    new String[]{String.valueOf(item.getId()), item.getUserId()}
            );

            success = (rowsAffected > 0);

            if (success) {
                db.setTransactionSuccessful();
                Log.d(TAG, "Successfully updated item: " + item.getName() + " (ID: " + item.getId() + ")");
            } else {
                Log.w(TAG, "No rows affected when updating item ID: " + item.getId());
            }

        } catch (SQLException e) {
            Log.e(TAG, "Database error updating item ID: " + item.getId(), e);
            success = false;
        } finally {
            if (db != null) {
                try {
                    db.endTransaction();
                } catch (SQLException e) {
                    Log.e(TAG, "Error ending transaction", e);
                }
            }
        }

        return success;
    }

    /**
     * Delete an inventory item by ID with proper validation
     *
     * @param id The ID of the item to delete
     * @return true if the deletion was successful, false otherwise
     * @throws IllegalArgumentException if id is invalid
     */
    public boolean deleteItem(long id) {
        if (id <= 0) {
            throw new IllegalArgumentException("Item ID must be positive");
        }

        SQLiteDatabase db = null;
        boolean success = false;

        try {
            db = getWritableDatabase();
            db.beginTransaction();

            int rowsAffected = db.delete(
                    TABLE_ITEMS,
                    COL_ID + " = ?",
                    new String[]{String.valueOf(id)}
            );

            success = (rowsAffected > 0);

            if (success) {
                db.setTransactionSuccessful();
                Log.d(TAG, "Successfully deleted item ID: " + id);
            } else {
                Log.w(TAG, "No rows affected when deleting item ID: " + id);
            }

        } catch (SQLException e) {
            Log.e(TAG, "Database error deleting item ID: " + id, e);
            success = false;
        } finally {
            if (db != null) {
                try {
                    db.endTransaction();
                } catch (SQLException e) {
                    Log.e(TAG, "Error ending transaction", e);
                }
            }
        }

        return success;
    }

    /**
     * Get the total count of items for a specific user
     *
     * @param userId The user ID to count items for
     * @return The number of items for the user, -1 on error
     * @throws IllegalArgumentException if userId is invalid
     */
    public int getItemCountByUser(String userId) {
        validateUserId(userId);

        SQLiteDatabase db = null;
        Cursor cursor = null;
        int count = -1;

        try {
            db = getReadableDatabase();
            cursor = db.rawQuery(
                    "SELECT COUNT(*) FROM " + TABLE_ITEMS + " WHERE " + COL_USER_ID + " = ?",
                    new String[]{userId}
            );

            if (cursor.moveToFirst()) {
                count = cursor.getInt(0);
            }

        } catch (SQLException e) {
            Log.e(TAG, "Database error counting items for user: " + userId, e);
            count = -1;
        } finally {
            closeCursor(cursor);
        }

        return count;
    }

    /**
     * Check if an item exists by ID and user ID
     *
     * @param id The item ID
     * @param userId The user ID
     * @return true if the item exists, false otherwise
     */
    public boolean itemExists(long id, String userId) {
        if (id <= 0) {
            return false;
        }

        validateUserId(userId);

        SQLiteDatabase db = null;
        Cursor cursor = null;
        boolean exists = false;

        try {
            db = getReadableDatabase();
            cursor = db.rawQuery(
                    "SELECT 1 FROM " + TABLE_ITEMS + " WHERE " + COL_ID + " = ? AND " + COL_USER_ID + " = ?",
                    new String[]{String.valueOf(id), userId}
            );

            exists = cursor.moveToFirst();

        } catch (SQLException e) {
            Log.e(TAG, "Database error checking if item exists: " + id, e);
            exists = false;
        } finally {
            closeCursor(cursor);
        }

        return exists;
    }

    // Private helper methods

    /**
     * Create an index on the user_id column for improved query performance
     *
     * @param db The database instance
     */
    private void createUserIdIndex(SQLiteDatabase db) throws SQLException {
        String indexSql = "CREATE INDEX IF NOT EXISTS idx_user_id ON " + TABLE_ITEMS + "(" + COL_USER_ID + ")";
        db.execSQL(indexSql);
        Log.d(TAG, "Created index on user_id column");
    }

    /**
     * Validate an inventory item for completeness and correctness
     *
     * @param item The item to validate
     * @throws IllegalArgumentException if validation fails
     */
    private void validateInventoryItem(InventoryItem item) {
        if (item == null) {
            throw new IllegalArgumentException(ERROR_INVALID_ITEM);
        }

        validateItemName(item.getName());
        validateQuantity(item.getQuantity());
        validateUserId(item.getUserId());
    }

    /**
     * Validate item name
     *
     * @param name The name to validate
     * @throws IllegalArgumentException if validation fails
     */
    private void validateItemName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException(ERROR_INVALID_NAME);
        }

        if (name.length() > maxNameLength) {
            throw new IllegalArgumentException(errorNameTooLong);
        }
    }

    /**
     * Validate quantity value
     *
     * @param quantity The quantity to validate
     * @throws IllegalArgumentException if validation fails
     */
    private void validateQuantity(int quantity) {
        if (quantity < minQuantity || quantity > maxQuantity) {
            throw new IllegalArgumentException(errorInvalidQuantity);
        }
    }

    /**
     * Validate user ID
     *
     * @param userId The user ID to validate
     * @throws IllegalArgumentException if validation fails
     */
    private void validateUserId(String userId) {
        if (userId == null || userId.trim().isEmpty()) {
            throw new IllegalArgumentException(ERROR_INVALID_USER_ID);
        }

        if (userId.length() > MAX_USER_ID_LENGTH) {
            throw new IllegalArgumentException(ERROR_USER_ID_TOO_LONG);
        }
    }

    /**
     * Create ContentValues from an inventory item
     *
     * @param item The inventory item
     * @return ContentValues for database operation
     */
    private ContentValues createContentValues(InventoryItem item) {
        ContentValues values = new ContentValues();
        values.put(COL_NAME, item.getName().trim());
        values.put(COL_QUANTITY, item.getQuantity());
        values.put(COL_USER_ID, item.getUserId().trim());
        return values;
    }

    /**
     * Extract inventory items from a database cursor
     *
     * @param cursor The cursor containing item data
     * @return List of inventory items
     */
    private List<InventoryItem> extractItemsFromCursor(Cursor cursor) {
        List<InventoryItem> items = new ArrayList<>();

        if (cursor != null && cursor.moveToFirst()) {
            do {
                try {
                    long id = cursor.getLong(cursor.getColumnIndexOrThrow(COL_ID));
                    String name = cursor.getString(cursor.getColumnIndexOrThrow(COL_NAME));
                    int quantity = cursor.getInt(cursor.getColumnIndexOrThrow(COL_QUANTITY));
                    String userId = cursor.getString(cursor.getColumnIndexOrThrow(COL_USER_ID));

                    items.add(new InventoryItem(id, name, quantity, userId));
                } catch (Exception e) {
                    Log.e(TAG, "Error extracting item from cursor", e);
                    // Continue processing other items
                }
            } while (cursor.moveToNext());
        }

        return items;
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