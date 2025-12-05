package com.example.expensemanager;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A lightweight Tokenizer for DistilBERT on Android.
 * Converts text into token IDs using a vocabulary file.
 */
public class SimpleTokenizer {
    private final Map<String, Integer> vocab = new HashMap<>();
    private final int MAX_LEN = 64; // Must match the value used during Python training

    public SimpleTokenizer(InputStream vocabStream) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(vocabStream));
        String line;
        int index = 0;

        // Read vocab.txt line by line.
        // Line 0 = ID 0, Line 1 = ID 1, etc.
        while ((line = reader.readLine()) != null) {
            vocab.put(line.trim(), index++);
        }
    }

    public long[] tokenize(String text) {
        List<Long> tokens = new ArrayList<>();

        // Add [CLS] token (Start of Sentence)
        // Usually ID 101 for BERT/DistilBERT
        if (vocab.containsKey("[CLS]")) {
            tokens.add((long) vocab.get("[CLS]"));
        } else {
            tokens.add(101L); // Fallback standard ID
        }

        // Basic normalization: Lowercase and split by whitespace
        // Note: A full WordPiece tokenizer is complex; this is a simplified version
        // that works well for whole words in SMS.
        String[] words = text.toLowerCase().split("\\s+");

        for (String word : words) {
            if (vocab.containsKey(word)) {
                tokens.add((long) vocab.get(word));
            } else {
                // If word not found, try to find subwords or use [UNK]
                // For simplicity in this lightweight version, we map unknown words to [UNK]
                if (vocab.containsKey("[UNK]")) {
                    tokens.add((long) vocab.get("[UNK]"));
                } else {
                    tokens.add(100L); // Fallback standard ID for UNK
                }
            }

            // Stop if we exceed max length (minus 1 for [SEP])
            if (tokens.size() >= MAX_LEN - 1) break;
        }

        // Add [SEP] token (End of Sentence)
        // Usually ID 102 for BERT/DistilBERT
        if (vocab.containsKey("[SEP]")) {
            tokens.add((long) vocab.get("[SEP]"));
        } else {
            tokens.add(102L); // Fallback standard ID
        }

        // Padding: Fill the rest of the array with 0s until MAX_LEN
        long[] result = new long[MAX_LEN];
        for (int i = 0; i < result.length; i++) {
            if (i < tokens.size()) {
                result[i] = tokens.get(i);
            } else {
                result[i] = 0; // [PAD] token is usually ID 0
            }
        }

        return result;
    }
}