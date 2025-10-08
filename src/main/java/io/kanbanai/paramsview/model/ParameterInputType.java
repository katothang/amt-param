package io.kanbanai.paramsview.model;

/**
 * Enum định nghĩa các loại input UI cho Jenkins parameters
 * 
 * Enum này cung cấp type safety cho việc xác định loại input phù hợp
 * với từng loại parameter, thay thế cho việc sử dụng String constants.
 * 
 * Mỗi input type tương ứng với một loại UI component khác nhau
 * trên frontend để hiển thị và thu thập input từ user.
 * 
 * @author KanbanAI
 * @since 1.0.2
 */
public enum ParameterInputType {
    
    /**
     * Input text đơn giản - cho StringParameterDefinition
     * Hiển thị như: <input type="text">
     */
    TEXT("text"),
    
    /**
     * Textarea cho text nhiều dòng - cho TextParameterDefinition
     * Hiển thị như: <textarea>
     */
    TEXTAREA("textarea"),
    
    /**
     * Dropdown select - cho ChoiceParameterDefinition và ChoiceParameter
     * Hiển thị như: <select><option>...</option></select>
     */
    SELECT("select"),
    
    /**
     * Checkbox cho boolean values - cho BooleanParameterDefinition
     * Hiển thị như: <input type="checkbox">
     */
    CHECKBOX("checkbox"),
    
    /**
     * Password input - cho PasswordParameterDefinition
     * Hiển thị như: <input type="password">
     */
    PASSWORD("password"),
    
    /**
     * Cascade select - cho CascadeChoiceParameter (Active Choices)
     * Dropdown có thể thay đổi choices dựa trên giá trị của parameters khác
     * Hiển thị như: <select> với JavaScript để handle cascade logic
     */
    CASCADE_SELECT("cascade_select"),
    
    /**
     * Dynamic reference - cho DynamicReferenceParameter (Active Choices)
     * Có thể hiển thị như text, HTML, hoặc các UI component khác
     * tùy thuộc vào script configuration
     */
    DYNAMIC_REFERENCE("dynamic_reference"),
    
    /**
     * File upload - cho FileParameterDefinition
     * Hiển thị như: <input type="file">
     */
    FILE("file"),
    
    /**
     * Hidden input - cho các parameters không cần user input
     * Hiển thị như: <input type="hidden">
     */
    HIDDEN("hidden"),
    
    /**
     * Multi-select - cho parameters cho phép chọn nhiều giá trị
     * Hiển thị như: <select multiple> hoặc checkbox group
     */
    MULTI_SELECT("multi_select"),
    
    /**
     * Radio buttons - alternative cho select khi có ít choices
     * Hiển thị như: <input type="radio"> group
     */
    RADIO("radio");
    
    /**
     * String value của input type (để serialize/deserialize)
     */
    private final String value;
    
    /**
     * Constructor
     * 
     * @param value String value của input type
     */
    ParameterInputType(String value) {
        this.value = value;
    }
    
    /**
     * Lấy string value của input type
     * 
     * @return String value
     */
    public String getValue() {
        return value;
    }
    
    /**
     * Tìm ParameterInputType từ string value
     * 
     * @param value String value cần tìm
     * @return ParameterInputType tương ứng, hoặc TEXT nếu không tìm thấy
     */
    public static ParameterInputType fromValue(String value) {
        if (value == null) {
            return TEXT;
        }
        
        for (ParameterInputType type : values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        
        return TEXT; // Default fallback
    }
    
    /**
     * Kiểm tra xem input type có hỗ trợ choices hay không
     * 
     * @return true nếu input type có thể có choices (SELECT, CASCADE_SELECT, MULTI_SELECT, RADIO)
     */
    public boolean supportsChoices() {
        return this == SELECT || 
               this == CASCADE_SELECT || 
               this == MULTI_SELECT || 
               this == RADIO;
    }
    
    /**
     * Kiểm tra xem input type có phải là dynamic hay không
     * Dynamic input types có thể thay đổi behavior dựa trên giá trị của parameters khác
     * 
     * @return true nếu là dynamic input type (CASCADE_SELECT, DYNAMIC_REFERENCE)
     */
    public boolean isDynamic() {
        return this == CASCADE_SELECT || this == DYNAMIC_REFERENCE;
    }
    
    /**
     * Kiểm tra xem input type có cần special handling hay không
     * 
     * @return true nếu cần special handling (PASSWORD, FILE, HIDDEN)
     */
    public boolean requiresSpecialHandling() {
        return this == PASSWORD || this == FILE || this == HIDDEN;
    }
    
    /**
     * Lấy input type phù hợp dựa trên Jenkins ParameterDefinition class name
     * 
     * @param parameterClassName Class name của ParameterDefinition
     * @return ParameterInputType phù hợp
     */
    public static ParameterInputType fromParameterClassName(String parameterClassName) {
        if (parameterClassName == null) {
            return TEXT;
        }
        
        // Jenkins built-in parameter types
        if (parameterClassName.contains("StringParameterDefinition")) {
            return TEXT;
        } else if (parameterClassName.contains("TextParameterDefinition")) {
            return TEXTAREA;
        } else if (parameterClassName.contains("BooleanParameterDefinition")) {
            return CHECKBOX;
        } else if (parameterClassName.contains("ChoiceParameterDefinition")) {
            return SELECT;
        } else if (parameterClassName.contains("PasswordParameterDefinition")) {
            return PASSWORD;
        } else if (parameterClassName.contains("FileParameterDefinition")) {
            return FILE;
        }
        
        // Active Choices Plugin parameter types
        else if (parameterClassName.contains("ChoiceParameter")) {
            return SELECT;
        } else if (parameterClassName.contains("CascadeChoiceParameter")) {
            return CASCADE_SELECT;
        } else if (parameterClassName.contains("DynamicReferenceParameter")) {
            return DYNAMIC_REFERENCE;
        }
        
        // Default fallback
        return TEXT;
    }
    
    @Override
    public String toString() {
        return value;
    }
}
