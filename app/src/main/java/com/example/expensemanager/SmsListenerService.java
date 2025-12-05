package com.example.expensemanager;

import android.app.Notification;
import android.content.Context;
import android.content.Intent;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;
import org.pytorch.IValue;
import org.pytorch.LiteModuleLoader;
import org.pytorch.Module;
import org.pytorch.Tensor;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SmsListenerService extends NotificationListenerService {

    public static final String ACTION_DEBUG_LOG = "com.example.expensemanager.DEBUG_LOG";
    private static final String TAG = "ExpenseAI"; // Keep for system log
    public static final String ACTION_SCAN_NOTIFICATIONS = "com.example.expensemanager.SCAN_NOTIFICATIONS";

    private AppDatabase db;
    private Module model;
    private SimpleTokenizer tokenizer;
    private final String[] CATEGORIES = {"Food", "Groceries", "Income", "Shopping", "Spam", "Subscription", "Transfer", "Transport", "Utilities"};

    @Override
    public void onCreate() {
        super.onCreate();
        db = AppDatabase.getDatabase(getApplicationContext());
        try {
            String modelPath = assetFilePath(this, "sms_model.ptl");
            model = LiteModuleLoader.load(modelPath);
            tokenizer = new SimpleTokenizer(getAssets().open("vocab.txt"));
            sendDebugLog("AI Model and Tokenizer loaded successfully.");
        } catch (Exception e) {
            sendDebugLog("FATAL: Error loading model or vocab: " + e.getMessage());
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_SCAN_NOTIFICATIONS.equals(intent.getAction())) {
            sendDebugLog("Scan initiated...");
            scanActiveNotifications();
        }
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn.isOngoing()) return;
        processNotification(sbn);
    }

    private void scanActiveNotifications() {
        StatusBarNotification[] activeNotifications = getActiveNotifications();
        if (activeNotifications != null) {
            sendDebugLog("Found " + activeNotifications.length + " notifications.");
            for (StatusBarNotification sbn : activeNotifications) {
                processNotification(sbn);
            }
        } else {
            sendDebugLog("Could not get notifications. Service may not be ready.");
        }
    }

    private void processNotification(StatusBarNotification sbn) {
        String packageName = sbn.getPackageName();
        sendDebugLog("Processing notification from: " + packageName);

        if (packageName.equals("com.google.android.apps.messaging") ||
            packageName.equals("com.samsung.android.messaging") ||
            packageName.equals("com.android.mms")) {

            Notification notification = sbn.getNotification();
            if (notification == null || notification.extras == null) {
                sendDebugLog("  -> Notification or extras were null. Ignoring.");
                return;
            }

            String text = notification.extras.getString(Notification.EXTRA_TEXT);
            String title = notification.extras.getString(Notification.EXTRA_TITLE);
            String fullMessage = (title != null ? title + " " : "") + (text != null ? text : "");

            if (!fullMessage.trim().isEmpty()) {
                sendDebugLog("  -> Message Text: '" + fullMessage + "'");
                if (isFinancialSms(fullMessage)) {
                    sendDebugLog("  -> SUCCESS: Found financial SMS! Classifying...");
                    classifyTransaction(fullMessage, sbn.getPostTime());
                } else {
                    sendDebugLog("  -> INFO: Ignoring non-financial message.");
                }
            } else {
                 sendDebugLog("  -> INFO: Ignoring empty message.");
            }
        } else {
            sendDebugLog("  -> INFO: Ignoring notification from non-SMS app.");
        }
    }

    private boolean isFinancialSms(String text) {
        String lower = text.toLowerCase();
        // Check for E-Mandate first
        if (lower.contains("e-mandate")) {
            sendDebugLog("    -> Detected E-Mandate SMS.");
            return isMandateForToday(text);
        }

        boolean isFinancial = lower.contains("rs") || lower.contains("inr") || lower.contains("₹") ||
               lower.contains("debited") || lower.contains("credited") ||
               lower.contains("spent") || lower.contains("paid") ||
               lower.contains("sent") || lower.contains("received") ||
               lower.contains("transaction") || lower.contains("payment");
        sendDebugLog("    -> isFinancialSms check result: " + isFinancial);
        return isFinancial;
    }

    private boolean isMandateForToday(String text) {
        // Regex to find dates like "On 02/12/25" or "On 02-12-2025"
        Pattern datePattern = Pattern.compile("on\\s*(\\d{2}[/-]\\d{2}[/-]\\d{2,4})", Pattern.CASE_INSENSITIVE);
        Matcher dateMatcher = datePattern.matcher(text);

        if (dateMatcher.find()) {
            try {
                String dateStr = dateMatcher.group(1).replace('-', '/');
                
                SimpleDateFormat sdf;
                if (dateStr.length() > 8) { // Format is likely dd/MM/yyyy
                    sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
                } else { // Format is likely dd/MM/yy
                    sdf = new SimpleDateFormat("dd/MM/yy", Locale.getDefault());
                }
                
                Date mandateDate = sdf.parse(dateStr);
                
                Calendar mandateCal = Calendar.getInstance();
                if (mandateDate != null) {
                    mandateCal.setTime(mandateDate);
                } else {
                     sendDebugLog("    -> E-Mandate date parsing resulted in null.");
                     return false;
                }

                Calendar todayCal = Calendar.getInstance();

                boolean isToday = mandateCal.get(Calendar.YEAR) == todayCal.get(Calendar.YEAR) &&
                                  mandateCal.get(Calendar.MONTH) == todayCal.get(Calendar.MONTH) &&
                                  mandateCal.get(Calendar.DAY_OF_MONTH) == todayCal.get(Calendar.DAY_OF_MONTH);
                
                sendDebugLog("    -> E-Mandate date check for " + dateStr + ". Is today? " + isToday);
                return isToday;

            } catch (Exception e) {
                sendDebugLog("    -> E-Mandate date parse error: " + e.getMessage());
                return false; 
            }
        }
        
        sendDebugLog("    -> E-Mandate text found, but no matching date pattern 'on dd/mm/yy'.");
        return false; // It's a mandate, but not in a format we can verify for today.
    }

    private void classifyTransaction(String text, long date) {
        if (model == null || tokenizer == null) {
            sendDebugLog("  -> ERROR: Model or tokenizer not initialized. Skipping classification.");
            return;
        }

        try {
            double amount = extractAmount(text);
            String summary = summarize(text, amount);

            new Thread(() -> {
                if (db.transactionDao().getCountByDescriptionAndDate(summary, date) > 0) {
                    sendDebugLog("  -> INFO: Ignoring duplicate transaction.");
                    return;
                }

                try {
                    long[] inputIds = tokenizer.tokenize(summary);
                    Tensor inputTensor = Tensor.fromBlob(inputIds, new long[]{1, 64});
                    long[] maskData = new long[64];
                    for (int i = 0; i < 64; i++) {
                        maskData[i] = (inputIds[i] != 0) ? 1 : 0;
                    }
                    Tensor maskTensor = Tensor.fromBlob(maskData, new long[]{1, 64});

                    IValue output = model.forward(IValue.from(inputTensor), IValue.from(maskTensor));
                    Tensor outputTensor = output.toTuple()[0].toTensor();
                    float[] scores = outputTensor.getDataAsFloatArray();

                    int categoryIdx = argmax(scores);
                    String detectedCategory = CATEGORIES[categoryIdx];
                    sendDebugLog("    -> AI Model classified as: " + detectedCategory);

                    if ("Spam".equals(detectedCategory)) {
                        sendDebugLog("    -> INFO: Ignored Spam Message.");
                        return;
                    }

                    int iconResId = getIconForCategory(detectedCategory, amount < 0);

                    Transaction transaction = new Transaction(summary, detectedCategory, amount, iconResId, date);
                    db.transactionDao().insert(transaction);
                    sendDebugLog("  -> SUCCESS: Transaction Saved!");
                } catch (Exception e) {
                    sendDebugLog("  -> FATAL: Error during classification: " + e.getMessage());
                }
            }).start();

        } catch (Exception e) {
            sendDebugLog("  -> FATAL: Error during classification: " + e.getMessage());
        }
    }

    private String summarize(String text, double amountValue) {
        // Regex to find amount, recipient, and sender
        Pattern amountPattern = Pattern.compile("(?:Rs\\.?|INR|₹)\\s*([\\d,]+\\.?\\d{0,2})");
        Pattern recipientPattern = Pattern.compile("(?:To|\\bVPA\\b)\\s*([A-Za-z\\s]+(?:\\s[A-Za-z]+)*)");
        Pattern senderPattern = Pattern.compile("From\\s*([A-Za-z\\s]+(?:Bank)?)");

        Matcher amountMatcher = amountPattern.matcher(text);

        if (amountMatcher.find()) {
            String amount = amountMatcher.group(0);
            String summary;
            if (amountValue < 0) { // Debit
                Matcher recipientMatcher = recipientPattern.matcher(text);
                String recipient = recipientMatcher.find() ? " to " + recipientMatcher.group(1).trim() : "";
                summary = "Sent " + amount + recipient;
            } else { // Credit
                Matcher senderMatcher = senderPattern.matcher(text);
                String sender = senderMatcher.find() ? " from " + senderMatcher.group(1).trim() : "";
                summary = "Received " + amount + sender;
            }
            return summary;
        }

        // If no amount is found, return the original text
        return text;
    }

    private int getIconForCategory(String category, boolean isDebit) {
        if (category == null) return isDebit ? R.drawable.ic_debit : R.drawable.ic_credit;
        switch (category) {
            case "Food":
                return R.drawable.ic_food;
            case "Income":
                return R.drawable.ic_income;
            case "Shopping":
                return R.drawable.ic_shopping;
            case "Subscription":
                return R.drawable.ic_subscription;
            case "Transport":
                return R.drawable.ic_transport;
            default:
                return isDebit ? R.drawable.ic_debit : R.drawable.ic_credit;
        }
    }

    private double extractAmount(String text) {
    Log.d(TAG, "Attempting to extract amount from: " + text);
    // This regex is simpler and just finds number-like sequences.
    Pattern pattern = Pattern.compile("([\\d,]+\\.?\\d{0,2})");
    Matcher matcher = pattern.matcher(text);

    // We need to find the number that is associated with a currency symbol or keyword.
    while (matcher.find()) {
        try {
            String potentialAmountStr = matcher.group(1);
            if (potentialAmountStr == null || potentialAmountStr.isEmpty()) {
                continue;
            }

            int matchStart = matcher.start(1);
            int matchEnd = matcher.end(1);

            // Define a "context window" around the number to look for keywords.
            int windowStart = Math.max(0, matchStart - 20);
            int windowEnd = Math.min(text.length(), matchEnd + 20);
            String contextWindow = text.substring(windowStart, windowEnd).toLowerCase();

            // Check if the context window contains financial keywords.
            if (contextWindow.contains("rs") || contextWindow.contains("inr") || contextWindow.contains("₹") ||
                contextWindow.contains("debited") || contextWindow.contains("credited") ||
                contextWindow.contains("spent") || contextWindow.contains("paid") ||
                contextWindow.contains("sent")) {

                String amountStr = potentialAmountStr.replaceAll(",", "");
                double amount = Double.parseDouble(amountStr);

                // Check for keywords to determine if it's an expense.
                if (contextWindow.contains("debited") || contextWindow.contains("spent") ||
                    contextWindow.contains("sent") || contextWindow.contains("paid")) {
                    Log.d(TAG, "Expense found: " + -amount);
                    return -amount;
                } else {
                    Log.d(TAG, "Income/other found: " + amount);
                    return amount;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Could not parse amount from: " + matcher.group(1), e);
        }
    }

    Log.d(TAG, "No financial amount found in the text.");
    return 0.0;
}

    private int argmax(float[] array) {
        int best = -1;
        float max = Float.MIN_VALUE;
        for (int i = 0; i < array.length; i++) {
            if (array[i] > max) {
                max = array[i];
                best = i;
            }
        }
        return best;
    }
    
    private void sendDebugLog(String message) {
        Intent intent = new Intent(ACTION_DEBUG_LOG);
        intent.putExtra("message", message);
        sendBroadcast(intent);
    }

    public static String assetFilePath(Context context, String assetName) throws IOException {
        File file = new File(context.getFilesDir(), assetName);
        if (file.exists() && file.length() > 0) {
            return file.getAbsolutePath();
        }
        try (InputStream is = context.getAssets().open(assetName)) {
            try (OutputStream os = new FileOutputStream(file)) {
                byte[] buffer = new byte[4 * 1024];
                int read;
                while ((read = is.read(buffer)) != -1) {
                    os.write(buffer, 0, read);
                }
                os.flush();
            }
            return file.getAbsolutePath();
        }
    }
}
