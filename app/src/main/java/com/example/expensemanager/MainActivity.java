package com.example.expensemanager;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.content.res.ResourcesCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements TransactionAdapter.OnTransactionLongClickListener {

    private TextView netSpendAmount;
    private RecyclerView transactionsRecyclerView;
    private TextView noTransactionsText;
    private TextView transactionsHeader;
    private Button permissionButton;
    private ImageButton scanButton;
    private ImageButton emptyScanButton;
    private ImageButton clearButton;
    private LinearLayout emptyStateLayout;
    private TransactionAdapter transactionAdapter;
    private AppDatabase db;
    private List<Transaction> currentTransactions = new ArrayList<>();
    private TextView headerTitle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        headerTitle = findViewById(R.id.header_title);
        netSpendAmount = findViewById(R.id.net_spend_amount);
        transactionsRecyclerView = findViewById(R.id.transactions_recycler_view);
        noTransactionsText = findViewById(R.id.no_transactions_text);
        transactionsHeader = findViewById(R.id.transactions_header);
        permissionButton = findViewById(R.id.permission_button);
        scanButton = findViewById(R.id.scan_button);
        emptyScanButton = findViewById(R.id.empty_scan_button);
        clearButton = findViewById(R.id.clear_button);
        emptyStateLayout = findViewById(R.id.empty_state_layout);
        CardView netSpendCard = findViewById(R.id.net_spend_card);

        db = AppDatabase.getDatabase(getApplicationContext());

        setupRecyclerView();
        observeTransactions();
        setMonthTitle();

        permissionButton.setOnClickListener(v -> openNotificationSettings());
        scanButton.setOnClickListener(v -> scanNotifications());
        emptyScanButton.setOnClickListener(v -> scanNotifications());
        clearButton.setOnClickListener(v -> clearTransactions());
        netSpendCard.setOnClickListener(v -> showSummaryDialog());
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateUIVisibility();
    }

    private void setupRecyclerView() {
        transactionAdapter = new TransactionAdapter(new ArrayList<>(), this);
        transactionsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        transactionsRecyclerView.setAdapter(transactionAdapter);
    }

    private void observeTransactions() {
        db.transactionDao().getAll().observe(this, transactions -> {
            this.currentTransactions = transactions;
            transactionAdapter.setTransactions(transactions);
            updateNetSpend(transactions);
            updateUIVisibility();
        });
    }

    private void setMonthTitle() {
        SimpleDateFormat monthFormat = new SimpleDateFormat("MMMM", Locale.getDefault());
        String currentMonth = monthFormat.format(new Date());
        String capitalizedMonth = currentMonth.substring(0, 1).toUpperCase() + currentMonth.substring(1).toLowerCase();
        headerTitle.setText(capitalizedMonth);
        Typeface typeface = ResourcesCompat.getFont(this, R.font.my_custom_font);
        headerTitle.setTypeface(typeface);
    }

    private void updateNetSpend(List<Transaction> transactions) {
        double totalDebit = 0;
        for (Transaction transaction : transactions) {
            if (transaction.getAmount() < 0) { // Only sum debit amounts
                totalDebit += transaction.getAmount();
            }
        }
        netSpendAmount.setText(String.format(Locale.getDefault(), "₹%.2f", Math.abs(totalDebit)));
    }

    private void updateUIVisibility() {
        boolean hasPermission = isNotificationServiceEnabled();
        boolean hasTransactions = transactionAdapter.getItemCount() > 0;

        clearButton.setVisibility(hasTransactions ? View.VISIBLE : View.GONE);

        if (hasTransactions) {
            transactionsRecyclerView.setVisibility(View.VISIBLE);
            transactionsHeader.setVisibility(View.VISIBLE);
            scanButton.setVisibility(hasPermission ? View.VISIBLE : View.GONE);
            emptyStateLayout.setVisibility(View.GONE);
        } else {
            transactionsRecyclerView.setVisibility(View.GONE);
            transactionsHeader.setVisibility(View.GONE);
            scanButton.setVisibility(View.GONE);
            emptyStateLayout.setVisibility(View.VISIBLE);

            if (hasPermission) {
                permissionButton.setVisibility(View.GONE);
                noTransactionsText.setVisibility(View.VISIBLE);
                emptyScanButton.setVisibility(View.VISIBLE);
            } else {
                permissionButton.setVisibility(View.VISIBLE);
                noTransactionsText.setVisibility(View.GONE);
                emptyScanButton.setVisibility(View.GONE);
            }
        }
    }

    private boolean isNotificationServiceEnabled() {
        String pkgName = getPackageName();
        final String flat = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        if (!TextUtils.isEmpty(flat)) {
            final String[] names = flat.split(":");
            for (String name : names) {
                final android.content.ComponentName cn = android.content.ComponentName.unflattenFromString(name);
                if (cn != null && TextUtils.equals(pkgName, cn.getPackageName())) {
                    return true;
                }
            }
        }
        return false;
    }

    private void showSummaryDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_summary, null);
        TextView essentialsSpent = dialogView.findViewById(R.id.essentials_spent);
        TextView nonEssentialsSpent = dialogView.findViewById(R.id.non_essentials_spent);
        TextView netAmount = dialogView.findViewById(R.id.net_amount);

        double essentials = 0;
        double nonEssentials = 0;
        double net = 0;

        for (Transaction transaction : currentTransactions) {
            double amount = transaction.getAmount();
            net += amount;

            if (amount < 0) { // Only consider debits for spending categories
                switch (transaction.getCategory()) {
                    case "Food":
                    case "Transport":
                        essentials += amount;
                        break;
                    case "Shopping":
                    case "Subscription":
                        nonEssentials += amount;
                        break;
                }
            }
        }

        essentialsSpent.setText(String.format(Locale.getDefault(), "Spent on Essentials: ₹%.2f", Math.abs(essentials)));
        nonEssentialsSpent.setText(String.format(Locale.getDefault(), "Spent on Non-Essentials: ₹%.2f", Math.abs(nonEssentials)));
        netAmount.setText(String.format(Locale.getDefault(), "Net Amount: ₹%.2f", net));

        new AlertDialog.Builder(this)
                .setView(dialogView)
                .setPositiveButton("Close", null)
                .show();
    }

    private void openNotificationSettings() {
        startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
    }

    private void scanNotifications() {
        Intent intent = new Intent(this, SmsListenerService.class);
        intent.setAction(SmsListenerService.ACTION_SCAN_NOTIFICATIONS);
        startService(intent);
    }

    private void clearTransactions() {
        new Thread(() -> {
            db.transactionDao().deleteAll();
        }).start();
    }

    @Override
    public void onTransactionLongClicked(Transaction transaction) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Transaction")
                .setMessage("Are you sure you want to delete this transaction?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    new Thread(() -> db.transactionDao().delete(transaction)).start();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
