package com.example.expensemanager;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "transactions")
public class Transaction {

    @PrimaryKey(autoGenerate = true)
    private int id;

    private final String description;
    private final String category;
    private final double amount;
    private final int iconResId;
    private final long date;

    public Transaction(String description, String category, double amount, int iconResId, long date) {
        this.description = description;
        this.category = category;
        this.amount = amount;
        this.iconResId = iconResId;
        this.date = date;
    }

    // --- Getters and Setters ---

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getDescription() {
        return description;
    }

    public String getCategory() {
        return category;
    }

    public double getAmount() {
        return amount;
    }

    public int getIconResId() {
        return iconResId;
    }

    public long getDate() {
        return date;
    }
}
