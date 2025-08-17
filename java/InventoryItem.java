package com.shaynesullivan.stockshark;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Objects;

/**
 * InventoryItem represents a single inventory item.
 * Each inventory item belongs to a specific user and contains essential information
 * including name, quantity, and unique identification for database operations.
 */
public class InventoryItem {

    // Validation constants - synchronized with integers.xml values
    private static final int MIN_QUANTITY = 0;
    private static final int MAX_QUANTITY = 999999;
    private static final int MAX_NAME_LENGTH = 255;
    private static final int MAX_USER_ID_LENGTH = 100;
    private static final long INVALID_ID = -1;

    // Error message constants
    private static final String ERROR_INVALID_ID = "Item ID must be non-negative";
    private static final String ERROR_INVALID_NAME = "Item name cannot be null or empty";
    private static final String ERROR_NAME_TOO_LONG = "Item name cannot exceed " + MAX_NAME_LENGTH + " characters";
    private static final String ERROR_INVALID_QUANTITY = "Quantity must be between " + MIN_QUANTITY + " and " + MAX_QUANTITY;
    private static final String ERROR_INVALID_USER_ID = "User ID cannot be null or empty";
    private static final String ERROR_USER_ID_TOO_LONG = "User ID cannot exceed " + MAX_USER_ID_LENGTH + " characters";

    // Instance variables
    private long id;
    private String name;
    private int quantity;
    private String userId;

    /**
     * Full constructor for creating an InventoryItem with all properties.
     * This is the primary constructor used when loading items from the database.
     *
     * @param id The unique database ID for this item (use -1 for new items)
     * @param name The display name of the inventory item
     * @param quantity The current quantity of the item
     * @param userId The ID of the user who owns this item
     * @throws IllegalArgumentException if any parameter is invalid
     */
    public InventoryItem(long id, @NonNull String name, int quantity, @NonNull String userId) {
        validateAndSetId(id);
        validateAndSetName(name);
        validateAndSetQuantity(quantity);
        validateAndSetUserId(userId);
    }

    /**
     * Constructor for creating new items without a database ID.
     * The ID will be set to -1 and should be updated after database insertion.
     *
     * @param name The display name of the inventory item
     * @param quantity The initial quantity of the item
     * @param userId The ID of the user who owns this item
     * @throws IllegalArgumentException if any parameter is invalid
     */
    public InventoryItem(@NonNull String name, int quantity, @NonNull String userId) {
        this(INVALID_ID, name, quantity, userId);
    }

    /**
     * Copy constructor for creating a new InventoryItem based on an existing one.
     *
     * @param other The InventoryItem to copy
     * @throws IllegalArgumentException if other is null
     */
    public InventoryItem(@NonNull InventoryItem other) {
        if (other == null) {
            throw new IllegalArgumentException("Cannot copy from null InventoryItem");
        }

        this.id = other.id;
        this.name = other.name;
        this.quantity = other.quantity;
        this.userId = other.userId;
    }

    // Getters

    /**
     * Get the unique database ID for this item.
     *
     * @return The item ID, or -1 if this is a new item not yet saved to database
     */
    public long getId() {
        return id;
    }

    /**
     * Get the display name of this inventory item.
     *
     * @return The item name (never null or empty)
     */
    @NonNull
    public String getName() {
        return name;
    }

    /**
     * Get the current quantity of this item.
     *
     * @return The quantity (always between MIN_QUANTITY and MAX_QUANTITY)
     */
    public int getQuantity() {
        return quantity;
    }

    /**
     * Get the ID of the user who owns this item.
     *
     * @return The user ID (never null or empty)
     */
    @NonNull
    public String getUserId() {
        return userId;
    }

    // Setters

    /**
     * Set the database ID for this item.
     * Should only be called after successful database insertion.
     *
     * @param id The new ID (must be non-negative, use -1 for new items)
     * @throws IllegalArgumentException if ID is invalid
     */
    public void setId(long id) {
        validateAndSetId(id);
    }

    /**
     * Set the name of this inventory item with validation.
     *
     * @param name The new name (cannot be null, empty, or exceed maximum length)
     * @throws IllegalArgumentException if name is invalid
     */
    public void setName(@NonNull String name) {
        validateAndSetName(name);
    }

    /**
     * Set the quantity of this item with validation.
     *
     * @param quantity The new quantity (must be within valid range)
     * @throws IllegalArgumentException if quantity is invalid
     */
    public void setQuantity(int quantity) {
        validateAndSetQuantity(quantity);
    }

    /**
     * Set the user ID for this item with validation.
     *
     * @param userId The new user ID (cannot be null, empty, or exceed maximum length)
     * @throws IllegalArgumentException if user ID is invalid
     */
    public void setUserId(@NonNull String userId) {
        validateAndSetUserId(userId);
    }

    // Utility methods

    /**
     * Check if this item has a valid database ID.
     *
     * @return true if the item has been saved to database, false otherwise
     */
    public boolean hasValidId() {
        return id > 0;
    }

    /**
     * Check if this item is new (not yet saved to database).
     *
     * @return true if this is a new item, false if it exists in database
     */
    public boolean isNewItem() {
        return id == INVALID_ID;
    }

    /**
     * Check if the item quantity is at the minimum threshold.
     *
     * @return true if quantity equals MIN_QUANTITY
     */
    public boolean isAtMinimumQuantity() {
        return quantity == MIN_QUANTITY;
    }

    /**
     * Check if the item quantity is at the maximum threshold.
     *
     * @return true if quantity equals MAX_QUANTITY
     */
    public boolean isAtMaximumQuantity() {
        return quantity == MAX_QUANTITY;
    }

    /**
     * Create a copy of this item with a modified quantity.
     *
     * @param newQuantity The new quantity value
     * @return A new InventoryItem with the updated quantity
     * @throws IllegalArgumentException if quantity is invalid
     */
    public InventoryItem withQuantity(int newQuantity) {
        return new InventoryItem(this.id, this.name, newQuantity, this.userId);
    }

    /**
     * Create a copy of this item with a modified name.
     *
     * @param newName The new name value
     * @return A new InventoryItem with the updated name
     * @throws IllegalArgumentException if name is invalid
     */
    public InventoryItem withName(@NonNull String newName) {
        return new InventoryItem(this.id, newName, this.quantity, this.userId);
    }

    // Validation helper methods

    /**
     * Validate and set the item ID
     *
     * @param id The ID to validate and set
     * @throws IllegalArgumentException if ID is invalid
     */
    private void validateAndSetId(long id) {
        if (id < INVALID_ID) {
            throw new IllegalArgumentException(ERROR_INVALID_ID);
        }
        this.id = id;
    }

    /**
     * Validate and set the item name
     *
     * @param name The name to validate and set
     * @throws IllegalArgumentException if name is invalid
     */
    private void validateAndSetName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException(ERROR_INVALID_NAME);
        }

        String trimmedName = name.trim();
        if (trimmedName.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException(ERROR_NAME_TOO_LONG);
        }

        this.name = trimmedName;
    }

    /**
     * Validate and set the item quantity
     *
     * @param quantity The quantity to validate and set
     * @throws IllegalArgumentException if quantity is invalid
     */
    private void validateAndSetQuantity(int quantity) {
        if (quantity < MIN_QUANTITY || quantity > MAX_QUANTITY) {
            throw new IllegalArgumentException(ERROR_INVALID_QUANTITY);
        }
        this.quantity = quantity;
    }

    /**
     * Validate and set the user ID
     *
     * @param userId The user ID to validate and set
     * @throws IllegalArgumentException if user ID is invalid
     */
    private void validateAndSetUserId(String userId) {
        if (userId == null || userId.trim().isEmpty()) {
            throw new IllegalArgumentException(ERROR_INVALID_USER_ID);
        }

        String trimmedUserId = userId.trim();
        if (trimmedUserId.length() > MAX_USER_ID_LENGTH) {
            throw new IllegalArgumentException(ERROR_USER_ID_TOO_LONG);
        }

        this.userId = trimmedUserId;
    }

    // Object contract methods

    /**
     * Compare this InventoryItem with another object for equality.
     * Two items are considered equal if all their properties match.
     *
     * @param obj The object to compare with
     * @return true if the objects are equal, false otherwise
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }

        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }

        InventoryItem that = (InventoryItem) obj;
        return id == that.id &&
                quantity == that.quantity &&
                Objects.equals(name, that.name) &&
                Objects.equals(userId, that.userId);
    }

    /**
     * Generate a hash code for this InventoryItem.
     * Uses all properties to ensure proper hash distribution.
     *
     * @return The hash code for this object
     */
    @Override
    public int hashCode() {
        return Objects.hash(id, name, quantity, userId);
    }

    /**
     * Generate a string representation of this InventoryItem.
     *
     * @return A comprehensive string representation
     */
    @Override
    @NonNull
    public String toString() {
        return "InventoryItem{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", quantity=" + quantity +
                ", userId='" + userId + '\'' +
                ", isNewItem=" + isNewItem() +
                '}';
    }

    /**
     * Generate a user-friendly display string for this item.
     *
     * @return A formatted string for display
     */
    public String toDisplayString() {
        return name + " (Qty: " + quantity + ")";
    }

    // Static factory methods

    /**
     * Create a new InventoryItem for a specific user with default values.
     *
     * @param userId The user ID for the new item
     * @return A new InventoryItem with default name and zero quantity
     * @throws IllegalArgumentException if userId is invalid
     */
    public static InventoryItem createNewItem(@NonNull String userId) {
        return new InventoryItem("New Item", 0, userId);
    }

    /**
     * Create an InventoryItem from basic parameters with validation.
     * This factory method provides an alternative to the constructor.
     *
     * @param name The item name
     * @param quantity The item quantity
     * @param userId The user ID
     * @return A new validated InventoryItem
     * @throws IllegalArgumentException if any parameter is invalid
     */
    public static InventoryItem create(@NonNull String name, int quantity, @NonNull String userId) {
        return new InventoryItem(name, quantity, userId);
    }

    // Constants getters for external validation

    /**
     * Get the minimum allowed quantity value.
     *
     * @return The minimum quantity constant
     */
    public static int getMinQuantity() {
        return MIN_QUANTITY;
    }

    /**
     * Get the maximum allowed quantity value.
     *
     * @return The maximum quantity constant
     */
    public static int getMaxQuantity() {
        return MAX_QUANTITY;
    }

    /**
     * Get the maximum allowed name length.
     *
     * @return The maximum name length constant
     */
    public static int getMaxNameLength() {
        return MAX_NAME_LENGTH;
    }

    /**
     * Get the maximum allowed user ID length.
     *
     * @return The maximum user ID length constant
     */
    public static int getMaxUserIdLength() {
        return MAX_USER_ID_LENGTH;
    }
}