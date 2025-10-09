package io.kanbanai.amtIntegration.util;

import io.kanbanai.amtIntegration.config.ValidationConstants;

import java.util.List;
import java.util.ArrayList;

/**
 * Utility class for JSON operations and string escaping.
 * 
 * This utility provides methods for safely converting Java objects to JSON format
 * without requiring external JSON libraries, keeping the plugin lightweight.
 * 
 * Flow:
 * 1. Provides JSON string escaping for safe serialization
 * 2. Handles array serialization with proper formatting
 * 3. Manages null value representation in JSON
 * 4. Filters empty or invalid values from collections
 * 
 * @author KanbanAI
 * @since 1.0.2
 */
public final class JsonUtils {
    
    // Prevent instantiation
    private JsonUtils() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }
    
    /**
     * Converts a string to JSON-safe format with proper escaping.
     * 
     * @param str the string to escape, can be null
     * @return JSON-safe string with quotes, or "null" if input is null
     */
    public static String toJsonString(String str) {
        if (str == null) {
            return ValidationConstants.DEFAULT_NULL_JSON;
        }
        
        String escaped = str;
        
        // Apply all escape replacements
        for (int i = 0; i < ValidationConstants.JSON_ESCAPE_CHARS.length; i++) {
            escaped = escaped.replace(
                ValidationConstants.JSON_ESCAPE_CHARS[i], 
                ValidationConstants.JSON_ESCAPE_REPLACEMENTS[i]
            );
        }
        
        return "\"" + escaped + "\"";
    }
    
    /**
     * Converts a list of strings to JSON array format.
     * Filters out null, empty, and invalid values.
     * 
     * @param list the list to convert, can be null
     * @return JSON array string representation
     */
    public static String toJsonArray(List<String> list) {
        if (list == null) {
            return "[]";
        }
        
        // Filter out invalid values
        List<String> filteredList = new ArrayList<>();
        for (String item : list) {
            if (isValidArrayItem(item)) {
                filteredList.add(item);
            }
        }
        
        if (filteredList.isEmpty()) {
            return "[]";
        }
        
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < filteredList.size(); i++) {
            sb.append(toJsonString(filteredList.get(i)));
            if (i < filteredList.size() - 1) {
                sb.append(",");
            }
        }
        sb.append("]");
        
        return sb.toString();
    }
    
    /**
     * Converts a boolean value to JSON format.
     * 
     * @param value the boolean value
     * @return "true" or "false" as string
     */
    public static String toJsonBoolean(boolean value) {
        return String.valueOf(value);
    }
    
    /**
     * Converts an integer value to JSON format.
     * 
     * @param value the integer value
     * @return string representation of the integer
     */
    public static String toJsonNumber(int value) {
        return String.valueOf(value);
    }
    
    /**
     * Serializes a parameter value that might be a single value or array.
     * 
     * @param value the parameter value to serialize
     * @return JSON representation (string or array)
     */
    public static String serializeParameterValue(String value) {
        if (value == null || value.trim().isEmpty()) {
            return toJsonString(value);
        }
        
        String trimmed = value.trim();
        
        // Check if already JSON array format
        if (trimmed.matches(ValidationConstants.JSON_ARRAY_PATTERN)) {
            return trimmed;
        }
        
        // Check if contains comma-separated values
        if (value.contains(",")) {
            String[] parts = value.split(",");
            
            // Filter and trim parts
            List<String> values = new ArrayList<>();
            for (String part : parts) {
                String trimmedPart = part.trim();
                if (!trimmedPart.isEmpty()) {
                    values.add(trimmedPart);
                }
            }
            
            // Return array if multiple values, string if single value
            if (values.size() > 1) {
                return toJsonArray(values);
            } else if (values.size() == 1) {
                return toJsonString(values.get(0));
            }
        }
        
        // Default: return as string
        return toJsonString(value);
    }
    
    /**
     * Cleans data field by removing empty array patterns.
     * 
     * @param data the data string to clean
     * @return cleaned data or null if only empty patterns
     */
    public static String cleanDataField(String data) {
        if (data == null || data.trim().isEmpty()) {
            return null;
        }
        
        // Remove all occurrences of empty brackets
        String cleaned = data.replaceAll(ValidationConstants.EMPTY_BRACKETS_PATTERN, "").trim();
        
        // Return null if nothing left after cleaning
        if (cleaned.isEmpty()) {
            return null;
        }
        
        return data;
    }
    
    /**
     * Checks if an item is valid for inclusion in JSON array.
     * 
     * @param item the item to check
     * @return true if item is valid, false otherwise
     */
    private static boolean isValidArrayItem(String item) {
        return item != null && 
               !item.trim().isEmpty() && 
               !item.trim().matches(ValidationConstants.EMPTY_BRACKETS_PATTERN);
    }
    
    /**
     * Creates a simple JSON object with key-value pairs.
     * 
     * @param pairs alternating key-value pairs (key1, value1, key2, value2, ...)
     * @return JSON object string
     */
    public static String createJsonObject(String... pairs) {
        if (pairs.length % 2 != 0) {
            throw new IllegalArgumentException("Pairs must be even number of arguments");
        }
        
        StringBuilder sb = new StringBuilder("{");
        
        for (int i = 0; i < pairs.length; i += 2) {
            if (i > 0) {
                sb.append(",");
            }
            
            String key = pairs[i];
            String value = pairs[i + 1];
            
            sb.append(toJsonString(key)).append(":").append(toJsonString(value));
        }
        
        sb.append("}");
        return sb.toString();
    }
}
