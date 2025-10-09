package io.kanbanai.paramsview.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Model class chứa thông tin chi tiết của một parameter đã được render
 * 
 * Class này đại diện cho một parameter cụ thể trong Jenkins job,
 * bao gồm tất cả thông tin cần thiết để hiển thị parameter trên UI
 * như tên, loại, giá trị hiện tại, các lựa chọn có sẵn, và dependencies.
 * 
 * Thay thế cho việc sử dụng generic Object hoặc Map, class này cung cấp
 * type safety và IDE support tốt hơn.
 * 
 * @author KanbanAI
 * @since 1.0.2
 */
public class RenderedParameterInfo {
    
    /**
     * Tên của parameter (unique trong một job)
     */
    private String name;
    
    /**
     * Loại parameter (class name của ParameterDefinition)
     * Ví dụ: "StringParameterDefinition", "ChoiceParameterDefinition", "ChoiceParameter"
     */
    private String type;
    
    /**
     * Mô tả của parameter (có thể null)
     */
    private String description;
    
    /**
     * Giá trị hiện tại của parameter
     * Có thể là default value hoặc giá trị được truyền vào từ request
     */
    private String currentValue;
    
    /**
     * Loại input UI phù hợp cho parameter này
     * Các giá trị có thể: "text", "textarea", "select", "checkbox", "password", 
     * "cascade_select", "dynamic_reference"
     */
    private ParameterInputType inputType;
    
    /**
     * Danh sách các lựa chọn có sẵn (cho dropdown, radio, checkbox)
     * Rỗng nếu parameter không có choices cố định
     */
    private List<String> choices;
    
    /**
     * Danh sách tên các parameters mà parameter này phụ thuộc vào
     * Sử dụng cho Cascade Choice Parameter hoặc Dynamic Reference Parameter
     */
    private List<String> dependencies;
    
    /**
     * True nếu parameter này là dynamic (Active Choices Plugin)
     * Dynamic parameters có thể thay đổi choices dựa trên giá trị của parameters khác
     */
    private boolean isDynamic;
    
    /**
     * True nếu parameter này là required (bắt buộc phải có giá trị)
     */
    private boolean isRequired;
    
    /**
     * Thông báo lỗi nếu có vấn đề khi render parameter
     */
    private String errorMessage;

    /**
     * Dữ liệu HTML hoặc nội dung đặc biệt cho DynamicReferenceParameter
     * Chỉ sử dụng cho parameters có inputType là DYNAMIC_REFERENCE
     */
    private String data;

    /**
     * Constructor mặc định
     */
    public RenderedParameterInfo() {
        this.choices = new ArrayList<>();
        this.dependencies = new ArrayList<>();
        this.inputType = ParameterInputType.TEXT;
        this.isDynamic = false;
        this.isRequired = false;
    }
    
    /**
     * Constructor với thông tin cơ bản
     * 
     * @param name Tên parameter
     * @param type Loại parameter
     * @param description Mô tả parameter
     */
    public RenderedParameterInfo(String name, String type, String description) {
        this();
        this.name = name;
        this.type = type;
        this.description = description;
    }
    
    // Getters and Setters
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getType() {
        return type;
    }
    
    public void setType(String type) {
        this.type = type;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public String getCurrentValue() {
        return currentValue;
    }
    
    public void setCurrentValue(String currentValue) {
        this.currentValue = currentValue;
    }
    
    public ParameterInputType getInputType() {
        return inputType;
    }
    
    public void setInputType(ParameterInputType inputType) {
        this.inputType = inputType != null ? inputType : ParameterInputType.TEXT;
    }
    
    public List<String> getChoices() {
        return choices;
    }
    
    public void setChoices(List<String> choices) {
        this.choices = choices != null ? choices : new ArrayList<>();
    }
    
    /**
     * Thêm một choice vào danh sách
     * 
     * @param choice Choice cần thêm
     */
    public void addChoice(String choice) {
        if (choice != null) {
            this.choices.add(choice);
        }
    }
    
    public List<String> getDependencies() {
        return dependencies;
    }
    
    public void setDependencies(List<String> dependencies) {
        this.dependencies = dependencies != null ? dependencies : new ArrayList<>();
    }
    
    /**
     * Thêm một dependency vào danh sách
     * 
     * @param dependency Tên parameter dependency
     */
    public void addDependency(String dependency) {
        if (dependency != null && !dependency.trim().isEmpty()) {
            this.dependencies.add(dependency.trim());
        }
    }
    
    public boolean isDynamic() {
        return isDynamic;
    }
    
    public void setDynamic(boolean dynamic) {
        isDynamic = dynamic;
    }
    
    public boolean isRequired() {
        return isRequired;
    }
    
    public void setRequired(boolean required) {
        isRequired = required;
    }
    
    public String getErrorMessage() {
        return errorMessage;
    }
    
    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }

    /**
     * Kiểm tra xem parameter có choices hay không
     * 
     * @return true nếu có ít nhất 1 choice, false nếu không
     */
    public boolean hasChoices() {
        return choices != null && !choices.isEmpty();
    }
    
    /**
     * Kiểm tra xem parameter có dependencies hay không
     * 
     * @return true nếu có ít nhất 1 dependency, false nếu không
     */
    public boolean hasDependencies() {
        return dependencies != null && !dependencies.isEmpty();
    }
    
    /**
     * Kiểm tra xem parameter có lỗi hay không
     *
     * @return true nếu có error message, false nếu không
     */
    public boolean hasError() {
        return errorMessage != null && !errorMessage.trim().isEmpty();
    }

    /**
     * Kiểm tra xem parameter có data hay không
     *
     * @return true nếu có data content, false nếu không
     */
    public boolean hasData() {
        return data != null && !data.trim().isEmpty();
    }

    /**
     * Chuyển đổi object thành JSON string
     * 
     * @return JSON string representation của object
     */
    public String toJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        
        // Basic information
        sb.append("\"name\":").append(jsonString(name)).append(",");
        sb.append("\"type\":").append(jsonString(type)).append(",");
        sb.append("\"description\":").append(jsonString(description)).append(",");
        sb.append("\"currentValue\":").append(jsonString(currentValue)).append(",");
        
        // Input type
        sb.append("\"inputType\":").append(jsonString(inputType != null ? inputType.getValue() : null)).append(",");
        
        // Flags
        sb.append("\"isDynamic\":").append(isDynamic).append(",");
        sb.append("\"isRequired\":").append(isRequired).append(",");
        
        // Error message
        sb.append("\"errorMessage\":").append(jsonString(errorMessage)).append(",");
        
        // Arrays
        sb.append("\"dependencies\":").append(jsonArray(dependencies)).append(",");
        sb.append("\"choices\":").append(jsonArray(choices)).append(",");

        // Data field for DynamicReferenceParameter
        sb.append("\"data\":").append(jsonString(data));

        sb.append("}");
        return sb.toString();
    }
    
    /**
     * Helper method để escape string cho JSON format
     */
    private String jsonString(String str) {
        if (str == null) {
            return "null";
        }
        
        String escaped = str.replace("\\", "\\\\")
                           .replace("\"", "\\\"")
                           .replace("\n", "\\n")
                           .replace("\r", "\\r")
                           .replace("\t", "\\t")
                           .replace("\b", "\\b")
                           .replace("\f", "\\f");
        
        return "\"" + escaped + "\"";
    }
    
    /**
     * Helper method để chuyển List<String> thành JSON array
     */
    private String jsonArray(List<String> list) {
        if (list == null) {
            return "[]";
        }
        
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            sb.append(jsonString(list.get(i)));
            if (i < list.size() - 1) {
                sb.append(",");
            }
        }
        sb.append("]");
        return sb.toString();
    }
    
    @Override
    public String toString() {
        return "RenderedParameterInfo{" +
                "name='" + name + '\'' +
                ", type='" + type + '\'' +
                ", inputType=" + inputType +
                ", isDynamic=" + isDynamic +
                ", choicesCount=" + (choices != null ? choices.size() : 0) +
                ", dependenciesCount=" + (dependencies != null ? dependencies.size() : 0) +
                ", hasData=" + (data != null && !data.trim().isEmpty()) +
                '}';
    }
}
