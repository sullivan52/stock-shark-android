package com.shaynesullivan.stockshark;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.Toast;
import android.view.View;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;

import java.util.ArrayList;
import java.util.List;

/**
 * DashboardActivity serves as the main hub of the Stock Shark application.
 * This activity displays the user's inventory items in a grid layout and provides
 * functionality to add, modify, and delete inventory items.
 *
 * @author Shayne Sullivan
 * @version 2.0
 */
public class DashboardActivity extends AppCompatActivity {

    // Constants
    private static final int GRID_LAYOUT_COLUMNS = 1;
    private static final int MAX_QUANTITY = 999999;
    private static final int MIN_QUANTITY = 0;
    private static final String USER_ID_KEY = "USER_ID";
    private static final String ERROR_USER_ID_MISSING = "User ID missing";
    private static final String ERROR_FIELDS_REQUIRED = "Both fields are required";
    private static final String ERROR_INVALID_NUMBER = "Please enter a valid number";
    private static final String ERROR_QUANTITY_RANGE = "Quantity must be between " + MIN_QUANTITY + " and " + MAX_QUANTITY;
    private static final String ERROR_DATABASE_OPERATION = "Database operation failed. Please try again.";
    private static final String SUCCESS_ITEM_DELETED = "Item deleted";
    private static final String DIALOG_TITLE_ADD_ITEM = "Add New Item";
    private static final String BUTTON_ADD = "Add";
    private static final String BUTTON_CANCEL = "Cancel";

    // UI Components
    private RecyclerView recyclerView;
    private InventoryAdapter adapter;
    private ExtendedFloatingActionButton fabAddItem;
    private MaterialToolbar toolbar;

    // Data Management
    private List<InventoryItem> inventoryList;
    private InventoryDatabase inventoryDatabase;
    private String userId; // The ID of the currently logged-in user

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        // Initialize components in logical order
        initializeDatabase();
        initializeUI();
        validateUserSession();
        loadInventoryData();
        setupRecyclerView();
        setupFloatingActionButton();
    }

    /**
     * Initialize the database connection
     */
    private void initializeDatabase() {
        inventoryDatabase = new InventoryDatabase(this);
    }

    /**
     * Initialize UI components and setup toolbar
     */
    private void initializeUI() {
        recyclerView = findViewById(R.id.recyclerViewInventory);
        fabAddItem = findViewById(R.id.fabAddItem);
        toolbar = findViewById(R.id.topAppBar);
        setSupportActionBar(toolbar);
    }

    /**
     * Validate user session and retrieve user ID from intent
     * Terminates activity if user ID is missing
     */
    private void validateUserSession() {
        userId = getIntent().getStringExtra(USER_ID_KEY);
        if (userId == null || userId.trim().isEmpty()) {
            showToast(ERROR_USER_ID_MISSING);
            finish();
            return;
        }
    }

    /**
     * Load inventory data specific to the current user
     */
    private void loadInventoryData() {
        try {
            inventoryList = inventoryDatabase.getItemsByUser(userId);
            if (inventoryList == null) {
                inventoryList = new ArrayList<>();
            }
        } catch (Exception e) {
            showToast(ERROR_DATABASE_OPERATION);
            inventoryList = new ArrayList<>();
        }
    }

    /**
     * Setup RecyclerView with adapter and layout manager
     */
    private void setupRecyclerView() {
        adapter = new InventoryAdapter(inventoryList, inventoryDatabase, this::handleItemDeletion, this);
        recyclerView.setLayoutManager(new GridLayoutManager(this, GRID_LAYOUT_COLUMNS));
        recyclerView.setAdapter(adapter);
    }

    /**
     * Setup floating action button click listener
     */
    private void setupFloatingActionButton() {
        fabAddItem.setOnClickListener(v -> showAddItemDialog());
    }

    /**
     * Handle item deletion with proper error handling
     *
     * @param item The inventory item to delete
     */
    private void handleItemDeletion(InventoryItem item) {
        try {
            if (inventoryList.remove(item)) {
                adapter.notifyDataSetChanged();
                showToast(SUCCESS_ITEM_DELETED);
            }
        } catch (Exception e) {
            showToast(ERROR_DATABASE_OPERATION);
        }
    }

    /**
     * Display dialog for adding new inventory items
     */
    private void showAddItemDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_add_item, null);
        EditText etDialogItemName = dialogView.findViewById(R.id.etDialogItemName);
        EditText etDialogItemQuantity = dialogView.findViewById(R.id.etDialogItemQuantity);

        new AlertDialog.Builder(this)
                .setTitle(DIALOG_TITLE_ADD_ITEM)
                .setView(dialogView)
                .setPositiveButton(BUTTON_ADD, (dialog, which) ->
                        handleAddItemSubmission(etDialogItemName, etDialogItemQuantity))
                .setNegativeButton(BUTTON_CANCEL, null)
                .show();
    }

    /**
     * Handle the submission of new item data from the dialog
     *
     * @param nameField EditText containing item name
     * @param quantityField EditText containing item quantity
     */
    private void handleAddItemSubmission(EditText nameField, EditText quantityField) {
        String name = nameField.getText().toString().trim();
        String quantityStr = quantityField.getText().toString().trim();

        // Validate input fields
        if (!validateItemInput(name, quantityStr)) {
            return;
        }

        // Parse and validate quantity
        int quantity;
        try {
            quantity = Integer.parseInt(quantityStr);
        } catch (NumberFormatException e) {
            showToast(ERROR_INVALID_NUMBER);
            return;
        }

        if (!validateQuantityRange(quantity)) {
            return;
        }

        // Create and add new item
        addNewInventoryItem(name, quantity);
    }

    /**
     * Validate that input fields are not empty
     *
     * @param name Item name
     * @param quantityStr Quantity as string
     * @return true if valid, false otherwise
     */
    private boolean validateItemInput(String name, String quantityStr) {
        if (name.isEmpty() || quantityStr.isEmpty()) {
            showToast(ERROR_FIELDS_REQUIRED);
            return false;
        }
        return true;
    }

    /**
     * Validate that quantity is within acceptable range
     *
     * @param quantity The quantity to validate
     * @return true if valid, false otherwise
     */
    private boolean validateQuantityRange(int quantity) {
        if (quantity < MIN_QUANTITY || quantity > MAX_QUANTITY) {
            showToast(ERROR_QUANTITY_RANGE);
            return false;
        }
        return true;
    }

    /**
     * Create and add a new inventory item to the database and list
     *
     * @param name Item name
     * @param quantity Item quantity
     */
    private void addNewInventoryItem(String name, int quantity) {
        try {
            InventoryItem newItem = new InventoryItem(0, name, quantity, userId);
            long id = inventoryDatabase.addItem(newItem);

            if (id > 0) {
                newItem.setId(id);
                inventoryList.add(newItem);
                adapter.notifyItemInserted(inventoryList.size() - 1);
                showToast("Item added successfully!");
            } else {
                showToast(ERROR_DATABASE_OPERATION);
            }
        } catch (IllegalArgumentException e) {
            // Handle validation errors specifically
            showToast("Validation error: " + e.getMessage());
        } catch (Exception e) {
            showToast(ERROR_DATABASE_OPERATION);
        }
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
     * Inflate the top app bar menu
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.top_app_bar_menu, menu);
        return true;
    }

    /**
     * Handle toolbar menu actions
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_notifications) {
            // Open the SMS notifications screen
            Intent intent = new Intent(this, SmsNotificationsActivity.class);
            startActivity(intent);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}