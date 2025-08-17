package com.shaynesullivan.stockshark;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

/**
 * InventoryAdapter manages the display and interaction of inventory items in a RecyclerView.
 * This adapter handles item display, quantity modifications, and deletion operations
 * with proper error handling and user feedback.
 */
public class InventoryAdapter extends RecyclerView.Adapter<InventoryAdapter.InventoryViewHolder> {

    // Constants
    private static final int QUANTITY_INCREMENT = 1;

    // Validation rules - passed from activity
    private final int minQuantity;
    private final int maxQuantity;

    // Error and success messages
    private final String errorMinQuantity;
    private final String errorMaxQuantity;
    private static final String ERROR_DATABASE_OPERATION = "Database operation failed. Please try again.";
    private static final String SUCCESS_ITEM_DELETED = "Item deleted successfully";

    // Data and dependencies
    private List<InventoryItem> inventoryList;
    private InventoryDatabase inventoryDatabase;
    private OnItemDeleteListener deleteListener;
    private Context context;

    /**
     * Listener interface to notify parent activity when an item is deleted
     */
    public interface OnItemDeleteListener {
        void onItemDelete(InventoryItem item);
    }

    /**
     * Constructor for InventoryAdapter
     *
     * @param inventoryList List of inventory items to display
     * @param inventoryDatabase Database instance for operations
     * @param listener Callback for item deletion events
     * @param context Context for showing toasts and accessing resources
     */
    public InventoryAdapter(List<InventoryItem> inventoryList, InventoryDatabase inventoryDatabase,
                            OnItemDeleteListener listener, Context context) {
        this.inventoryList = inventoryList;
        this.inventoryDatabase = inventoryDatabase;
        this.deleteListener = listener;
        this.context = context;

        // Load validation constants from resources
        this.minQuantity = context.getResources().getInteger(R.integer.min_quantity);
        this.maxQuantity = context.getResources().getInteger(R.integer.max_quantity);

        // Build dynamic error messages
        this.errorMinQuantity = "Minimum quantity is " + minQuantity;
        this.errorMaxQuantity = "Maximum quantity is " + maxQuantity;
    }

    /**
     * Creates new ViewHolder instances for inventory items
     *
     * @param parent The parent ViewGroup
     * @param viewType The view type (unused in this adapter)
     * @return A new InventoryViewHolder instance
     */
    @NonNull
    @Override
    public InventoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_inventory_card, parent, false);
        return new InventoryViewHolder(view);
    }

    /**
     * Binds data to ViewHolder and sets up click listeners
     *
     * @param holder The ViewHolder to bind data to
     * @param position The position of the item in the list
     */
    @Override
    public void onBindViewHolder(@NonNull InventoryViewHolder holder, int position) {
        InventoryItem item = inventoryList.get(position);

        // Bind data to views
        bindItemData(holder, item);

        // Setup click listeners
        setupClickListeners(holder, item, position);
    }

    /**
     * Bind inventory item data to the ViewHolder views
     *
     * @param holder The ViewHolder containing the views
     * @param item The inventory item to display
     */
    private void bindItemData(InventoryViewHolder holder, InventoryItem item) {
        holder.tvItemName.setText(item.getName());
        holder.tvItemQuantity.setText(String.valueOf(item.getQuantity()));
    }

    /**
     * Setup click listeners for all interactive elements
     *
     * @param holder The ViewHolder containing the views
     * @param item The inventory item
     * @param position The position in the list
     */
    private void setupClickListeners(InventoryViewHolder holder, InventoryItem item, int position) {
        // Delete button
        holder.btnDelete.setOnClickListener(v -> handleDeleteItem(item, position));

        // Increase quantity button
        holder.btnIncrease.setOnClickListener(v -> handleIncreaseQuantity(holder, item));

        // Decrease quantity button
        holder.btnDecrease.setOnClickListener(v -> handleDecreaseQuantity(holder, item));
    }

    /**
     * Handle item deletion with proper error handling and UI updates
     *
     * @param item The item to delete
     * @param position The position of the item in the list
     */
    private void handleDeleteItem(InventoryItem item, int position) {
        try {
            // Delete from database first
            boolean deleted = inventoryDatabase.deleteItem(item.getId());

            if (deleted) {
                // Remove from list and update UI
                inventoryList.remove(position);
                notifyItemRemoved(position);
                notifyItemRangeChanged(position, inventoryList.size());

                // Notify parent activity
                if (deleteListener != null) {
                    deleteListener.onItemDelete(item);
                }

                showToast(item.getName() + " deleted successfully");
            } else {
                showToast(ERROR_DATABASE_OPERATION);
            }
        } catch (Exception e) {
            showToast(ERROR_DATABASE_OPERATION);
        }
    }

    /**
     * Handle quantity increase with validation and error handling
     *
     * @param holder The ViewHolder for UI updates
     * @param item The item to modify
     */
    private void handleIncreaseQuantity(InventoryViewHolder holder, InventoryItem item) {
        int currentQuantity = item.getQuantity();

        // Validate maximum quantity
        if (currentQuantity >= maxQuantity) {
            showToast(errorMaxQuantity);
            return;
        }

        int newQuantity = currentQuantity + QUANTITY_INCREMENT;
        updateItemQuantity(holder, item, newQuantity);
    }

    /**
     * Handle quantity decrease with validation and error handling
     *
     * @param holder The ViewHolder for UI updates
     * @param item The item to modify
     */
    private void handleDecreaseQuantity(InventoryViewHolder holder, InventoryItem item) {
        int currentQuantity = item.getQuantity();

        // Validate minimum quantity
        if (currentQuantity <= minQuantity) {
            showToast(errorMinQuantity);
            return;
        }

        int newQuantity = currentQuantity - QUANTITY_INCREMENT;
        updateItemQuantity(holder, item, newQuantity);
    }

    /**
     * Update item quantity in database and UI
     *
     * @param holder The ViewHolder for UI updates
     * @param item The item to update
     * @param newQuantity The new quantity value
     */
    private void updateItemQuantity(InventoryViewHolder holder, InventoryItem item, int newQuantity) {
        int previousQuantity = item.getQuantity(); // Store for potential revert

        try {
            // Update item object
            item.setQuantity(newQuantity);

            // Update database
            boolean updated = inventoryDatabase.updateItem(item);

            if (updated) {
                // Update UI
                holder.tvItemQuantity.setText(String.valueOf(newQuantity));
            } else {
                // Revert item quantity if database update failed
                item.setQuantity(previousQuantity);
                showToast(ERROR_DATABASE_OPERATION);
            }
        } catch (Exception e) {
            // Revert item quantity if exception occurred
            item.setQuantity(previousQuantity);
            showToast(ERROR_DATABASE_OPERATION);
        }
    }

    /**
     * Returns the total number of items in the list
     *
     * @return The size of the inventory list
     */
    @Override
    public int getItemCount() {
        return inventoryList != null ? inventoryList.size() : 0;
    }

    /**
     * Update the adapter's data list and refresh the view
     *
     * @param newList The new list of inventory items
     */
    public void updateList(List<InventoryItem> newList) {
        if (newList != null) {
            inventoryList = newList;
            notifyDataSetChanged();
        }
    }

    /**
     * Get the current inventory list
     *
     * @return The current list of inventory items
     */
    public List<InventoryItem> getInventoryList() {
        return inventoryList;
    }

    /**
     * Utility method to show toast messages
     *
     * @param message The message to display
     */
    private void showToast(String message) {
        if (context != null) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * ViewHolder class to hold references to views in the inventory item layout
     */
    public static class InventoryViewHolder extends RecyclerView.ViewHolder {
        TextView tvItemName;
        TextView tvItemQuantity;
        TextView btnIncrease;
        TextView btnDecrease;
        ImageButton btnDelete;

        /**
         * Constructor for InventoryViewHolder
         *
         * @param itemView The view representing a single inventory item
         */
        public InventoryViewHolder(@NonNull View itemView) {
            super(itemView);
            initializeViews(itemView);
        }

        /**
         * Initialize view references
         *
         * @param itemView The item view containing the UI elements
         */
        private void initializeViews(View itemView) {
            tvItemName = itemView.findViewById(R.id.tvItemName);
            tvItemQuantity = itemView.findViewById(R.id.tvItemQuantity);
            btnIncrease = itemView.findViewById(R.id.btnIncrease);
            btnDecrease = itemView.findViewById(R.id.btnDecrease);
            btnDelete = itemView.findViewById(R.id.btnDelete);
        }
    }
}