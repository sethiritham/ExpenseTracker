# MyExpenseApp

## Project Overview

MyExpenseApp is a simple Android application designed to help users track their expenses. It provides a clean and intuitive interface for managing financial transactions, with a key feature of automatically adding expenses by scanning SMS notifications. The app also leverages a PyTorch model for intelligent transaction categorization.

## Features

*   **Expense Tracking:** Manually add, edit, and delete expense transactions.
*   **SMS Scanning:** Automatically scans incoming SMS messages for transaction information and adds them to the expense list.
*   **Transaction Categorization:** Uses a PyTorch model to automatically categorize expenses.
*   **Monthly Summary:** Provides a summary of expenses for the current month.
*   **Local Data Storage:** Uses Room persistence library to store all transaction data locally on the device.

## Technologies Used

*   **Android SDK:** The official Android development kit.
*   **Java:** The primary programming language for the app.
*   **Room Persistence Library:** For local data storage.
*   **PyTorch Mobile:** For on-device machine learning.
*   **AndroidX Libraries:** A collection of libraries that provide backward-compatibility and other features.
*   **Material Components for Android:** A set of UI components that follow Material Design guidelines.

## Setup

1.  **Clone the repository:**
    ```bash
    git clone https://github.com/your-username/MyExpenseApp.git
    ```
2.  **Open in Android Studio:**
    Open the cloned project in Android Studio.
3.  **Build the project:**
    Sync the project with Gradle files and build the app.
4.  **Run the app:**
    Run the app on an Android emulator or a physical device.

## Permissions

The app requires the following permission:

*   **Notification Listener:** To read incoming notifications and automatically add transactions.
