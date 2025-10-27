package io.kanbanai.amtIntegration.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Model class representing a choice item with key-value pair.
 *
 * This class is used for choice parameters (select, radio, etc.)
 * where each choice has a key (value to submit) and a value (display text).
 *
 * This replaces the use of Map<String, String> for better type safety
 * and clearer intent.
 *
 * Flow:
 * 1. Stores choice key (value to be submitted to Jenkins)
 * 2. Stores choice value (text to be displayed to user)
 * 3. Provides JSON serialization for API responses
 * 4. Supports equality and hash code for collections
 *
 * @author KanbanAI
 * @since 1.0.5
 */
public class ChoiceItem {
    
    /**
     * Key of the choice (value to submit to Jenkins)
     */
    @JsonProperty("key")
    private String key;
    
    /**
     * Value of the choice (display text for user)
     */
    @JsonProperty("value")
    private String value;
    
    /**
     * Default constructor for Jackson deserialization
     */
    public ChoiceItem() {
    }
    
    /**
     * Constructor with key and value
     *
     * @param key Key of the choice (value to submit)
     * @param value Value of the choice (display text)
     */
    public ChoiceItem(String key, String value) {
        this.key = key;
        this.value = value;
    }
    
    /**
     * Gets the key of the choice
     *
     * @return Key (value to submit)
     */
    public String getKey() {
        return key;
    }
    
    /**
     * Sets the key of the choice
     *
     * @param key Key (value to submit)
     */
    public void setKey(String key) {
        this.key = key;
    }
    
    /**
     * Gets the value of the choice
     *
     * @return Value (display text)
     */
    public String getValue() {
        return value;
    }
    
    /**
     * Sets the value of the choice
     *
     * @param value Value (display text)
     */
    public void setValue(String value) {
        this.value = value;
    }
    
    
}

