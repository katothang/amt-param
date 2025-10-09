package io.kanbanai.amtIntegration.util;

import io.kanbanai.amtIntegration.config.ValidationConstants;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class for parsing parameter values from various input formats.
 * 
 * This utility handles parsing of parameter values from query strings,
 * JSON objects, and other input formats commonly used in Jenkins APIs.
 * 
 * Flow:
 * 1. Parses parameter strings in key:value format
 * 2. Handles JSON object parsing for parameter values
 * 3. Supports array value parsing with proper formatting
 * 4. Validates parameter names and values
 * 
 * @author KanbanAI
 * @since 1.0.2
 */
public final class ParameterParsingUtils {
    
    // Prevent instantiation
    private ParameterParsingUtils() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }
    
    // Compiled patterns for better performance
    private static final Pattern PARAMETER_VALUE_PATTERN = Pattern.compile(ValidationConstants.PARAMETER_VALUE_PATTERN);
    private static final Pattern JSON_ARRAY_PATTERN = Pattern.compile(ValidationConstants.JSON_ARRAY_PATTERN);
    
    /**
     * Parses parameter values from a comma-separated string.
     * 
     * Expected format: "param1:value1,param2:value2,param3:[v1,v2,v3]"
     * 
     * @param paramsStr the parameter string to parse, can be null
     * @return map of parameter names to values, never null
     */
    public static Map<String, String> parseParameterValues(String paramsStr) {
        Map<String, String> result = new HashMap<>();
        
        if (paramsStr == null || paramsStr.trim().isEmpty()) {
            return result;
        }
        
        // Split parameters while respecting brackets
        List<String> pairs = splitParametersRespectingBrackets(paramsStr);

        for (String pair : pairs) {
            if (pair == null || pair.trim().isEmpty()) {
                continue;
            }

            Matcher matcher = PARAMETER_VALUE_PATTERN.matcher(pair.trim());
            if (matcher.matches()) {
                String key = matcher.group(1).trim();
                String value = matcher.group(2).trim();

                // Remove array brackets if present and extract comma-separated values
                if (value.startsWith("[") && value.endsWith("]")) {
                    value = value.substring(1, value.length() - 1);
                }

                if (isValidParameterName(key)) {
                    result.put(key, value);
                }
            }
        }
        
        return result;
    }

    /**
     * Splits parameter string while respecting brackets.
     * This ensures that commas inside brackets are not treated as parameter separators.
     *
     * @param paramsStr the parameter string to split
     * @return list of parameter pairs
     */
    private static List<String> splitParametersRespectingBrackets(String paramsStr) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int bracketDepth = 0;

        for (int i = 0; i < paramsStr.length(); i++) {
            char c = paramsStr.charAt(i);

            if (c == '[') {
                bracketDepth++;
                current.append(c);
            } else if (c == ']') {
                bracketDepth--;
                current.append(c);
            } else if (c == ',' && bracketDepth == 0) {
                // This comma is a parameter separator, not inside brackets
                if (current.length() > 0) {
                    result.add(current.toString().trim());
                    current.setLength(0);
                }
            } else {
                current.append(c);
            }
        }

        // Add the last parameter
        if (current.length() > 0) {
            result.add(current.toString().trim());
        }

        return result;
    }

    /**
     * Parses a JSON object string into parameter map.
     * 
     * Expected format: {"param1": "value1", "param2": "value2", "param3": "[v1,v2,v3]"}
     * 
     * @param jsonObject the JSON object string to parse
     * @return map of parameter names to values, never null
     */
    public static Map<String, String> parseJsonObject(String jsonObject) {
        Map<String, String> result = new HashMap<>();
        
        if (jsonObject == null || jsonObject.trim().isEmpty()) {
            return result;
        }
        
        try {
            // Remove outer braces
            String content = jsonObject.trim();
            if (content.startsWith("{") && content.endsWith("}")) {
                content = content.substring(1, content.length() - 1);
            }
            
            // Simple JSON parsing (avoiding external dependencies)
            String[] pairs = splitJsonPairs(content);
            
            for (String pair : pairs) {
                String[] keyValue = parseJsonPair(pair);
                if (keyValue != null && keyValue.length == 2) {
                    String key = keyValue[0];
                    String value = keyValue[1];
                    
                    if (isValidParameterName(key)) {
                        result.put(key, value);
                    }
                }
            }
            
        } catch (Exception e) {
            // Return empty map on parsing error
            return new HashMap<>();
        }
        
        return result;
    }
    
    /**
     * Validates if a parameter name is valid according to naming rules.
     * 
     * @param parameterName the parameter name to validate
     * @return true if valid, false otherwise
     */
    public static boolean isValidParameterName(String parameterName) {
        if (parameterName == null || parameterName.trim().isEmpty()) {
            return false;
        }
        
        String trimmed = parameterName.trim();
        
        // Check length
        if (trimmed.length() > ValidationConstants.MAX_PARAMETER_NAME_LENGTH) {
            return false;
        }
        
        // Check pattern
        return trimmed.matches(ValidationConstants.PARAMETER_NAME_PATTERN);
    }
    
    /**
     * Validates if a parameter value is within acceptable limits.
     * 
     * @param parameterValue the parameter value to validate
     * @return true if valid, false otherwise
     */
    public static boolean isValidParameterValue(String parameterValue) {
        if (parameterValue == null) {
            return true; // null values are acceptable
        }
        
        return parameterValue.length() <= ValidationConstants.MAX_PARAMETER_VALUE_LENGTH;
    }
    
    /**
     * Finds the matching closing brace for a JSON object.
     * 
     * @param text the text to search in
     * @param startIndex the starting index of the opening brace
     * @return index of matching closing brace, or -1 if not found
     */
    public static int findMatchingBrace(String text, int startIndex) {
        if (startIndex >= text.length() || text.charAt(startIndex) != '{') {
            return -1;
        }
        
        int braceCount = 1;
        boolean inString = false;
        boolean escaped = false;
        
        for (int i = startIndex + 1; i < text.length(); i++) {
            char c = text.charAt(i);
            
            if (escaped) {
                escaped = false;
                continue;
            }
            
            if (c == '\\') {
                escaped = true;
                continue;
            }
            
            if (c == '"') {
                inString = !inString;
                continue;
            }
            
            if (!inString) {
                if (c == '{') {
                    braceCount++;
                } else if (c == '}') {
                    braceCount--;
                    if (braceCount == 0) {
                        return i;
                    }
                }
            }
        }
        
        return -1; // No matching brace found
    }
    
    /**
     * Splits JSON content into key-value pairs, handling nested objects and arrays.
     * 
     * @param content the JSON content without outer braces
     * @return array of key-value pair strings
     */
    private static String[] splitJsonPairs(String content) {
        // Simple implementation - could be enhanced for complex nested structures
        return content.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");
    }
    
    /**
     * Parses a single JSON key-value pair.
     * 
     * @param pair the pair string to parse
     * @return array with [key, value] or null if parsing fails
     */
    private static String[] parseJsonPair(String pair) {
        if (pair == null || pair.trim().isEmpty()) {
            return null;
        }
        
        String trimmed = pair.trim();
        int colonIndex = trimmed.indexOf(':');
        
        if (colonIndex == -1) {
            return null;
        }
        
        String key = trimmed.substring(0, colonIndex).trim();
        String value = trimmed.substring(colonIndex + 1).trim();
        
        // Remove quotes from key and value
        key = removeQuotes(key);
        value = removeQuotes(value);
        
        return new String[]{key, value};
    }
    
    /**
     * Removes surrounding quotes from a string if present.
     * 
     * @param str the string to process
     * @return string without surrounding quotes
     */
    private static String removeQuotes(String str) {
        if (str == null || str.length() < 2) {
            return str;
        }
        
        if (str.startsWith("\"") && str.endsWith("\"")) {
            return str.substring(1, str.length() - 1);
        }
        
        return str;
    }
    
    /**
     * Normalizes parameter value by handling array formats and special characters.
     * 
     * @param value the value to normalize
     * @return normalized value
     */
    public static String normalizeParameterValue(String value) {
        if (value == null) {
            return null;
        }
        
        String trimmed = value.trim();
        
        // Handle array format
        if (JSON_ARRAY_PATTERN.matcher(trimmed).matches()) {
            return trimmed;
        }
        
        // Handle comma-separated values
        if (trimmed.contains(",") && !trimmed.startsWith("[")) {
            String[] parts = trimmed.split(",");
            StringBuilder sb = new StringBuilder("[");
            
            for (int i = 0; i < parts.length; i++) {
                if (i > 0) {
                    sb.append(",");
                }
                sb.append("\"").append(parts[i].trim()).append("\"");
            }
            
            sb.append("]");
            return sb.toString();
        }
        
        return trimmed;
    }
}
