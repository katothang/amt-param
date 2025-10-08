package io.kanbanai.paramsview.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Model class chứa thông tin đầy đủ về tất cả parameters của một Jenkins job
 * 
 * Class này đại diện cho response data của API, bao gồm thông tin về job
 * và danh sách tất cả parameters đã được render với đầy đủ thông tin.
 * 
 * Thay thế cho việc sử dụng generic Object hoặc Map, class này cung cấp
 * type safety và IDE support tốt hơn.
 * 
 * @author KanbanAI
 * @since 1.0.2
 */
public class RenderedParametersInfo {
    
    /**
     * Tên ngắn của job (không bao gồm folder path)
     */
    private String jobName;
    
    /**
     * Tên đầy đủ của job (bao gồm folder path nếu có)
     * Ví dụ: "folder1/folder2/jobName"
     */
    private String jobFullName;
    
    /**
     * URL tương đối của job trong Jenkins
     * Ví dụ: "job/folder1/job/jobName/"
     */
    private String jobUrl;
    
    /**
     * URL để trigger build với parameters
     * Ví dụ: "job/folder1/job/jobName/buildWithParameters"
     */
    private String buildWithParametersUrl;
    
    /**
     * Danh sách tất cả parameters của job đã được render
     */
    private List<RenderedParameterInfo> parameters;
    
    /**
     * Thông tin về Active Choices plugin availability
     */
    private boolean activeChoicesPluginAvailable;
    
    /**
     * Version của Active Choices plugin (nếu có)
     */
    private String activeChoicesPluginVersion;
    
    /**
     * Constructor mặc định
     */
    public RenderedParametersInfo() {
        this.parameters = new ArrayList<>();
    }
    
    /**
     * Constructor với thông tin job cơ bản
     * 
     * @param jobName Tên job
     * @param jobFullName Tên đầy đủ của job
     * @param jobUrl URL của job
     */
    public RenderedParametersInfo(String jobName, String jobFullName, String jobUrl) {
        this();
        this.jobName = jobName;
        this.jobFullName = jobFullName;
        this.jobUrl = jobUrl;
        this.buildWithParametersUrl = jobUrl + "buildWithParameters";
    }
    
    // Getters and Setters
    
    public String getJobName() {
        return jobName;
    }
    
    public void setJobName(String jobName) {
        this.jobName = jobName;
    }
    
    public String getJobFullName() {
        return jobFullName;
    }
    
    public void setJobFullName(String jobFullName) {
        this.jobFullName = jobFullName;
    }
    
    public String getJobUrl() {
        return jobUrl;
    }
    
    public void setJobUrl(String jobUrl) {
        this.jobUrl = jobUrl;
        // Tự động update buildWithParametersUrl khi jobUrl thay đổi
        if (jobUrl != null) {
            this.buildWithParametersUrl = jobUrl + "buildWithParameters";
        }
    }
    
    public String getBuildWithParametersUrl() {
        return buildWithParametersUrl;
    }
    
    public void setBuildWithParametersUrl(String buildWithParametersUrl) {
        this.buildWithParametersUrl = buildWithParametersUrl;
    }
    
    public List<RenderedParameterInfo> getParameters() {
        return parameters;
    }
    
    public void setParameters(List<RenderedParameterInfo> parameters) {
        this.parameters = parameters != null ? parameters : new ArrayList<>();
    }
    
    /**
     * Thêm một parameter vào danh sách
     * 
     * @param parameter Parameter cần thêm
     */
    public void addParameter(RenderedParameterInfo parameter) {
        if (parameter != null) {
            this.parameters.add(parameter);
        }
    }
    
    public boolean isActiveChoicesPluginAvailable() {
        return activeChoicesPluginAvailable;
    }
    
    public void setActiveChoicesPluginAvailable(boolean activeChoicesPluginAvailable) {
        this.activeChoicesPluginAvailable = activeChoicesPluginAvailable;
    }
    
    public String getActiveChoicesPluginVersion() {
        return activeChoicesPluginVersion;
    }
    
    public void setActiveChoicesPluginVersion(String activeChoicesPluginVersion) {
        this.activeChoicesPluginVersion = activeChoicesPluginVersion;
    }
    
    /**
     * Chuyển đổi object thành JSON string
     * 
     * Sử dụng StringBuilder để tạo JSON thay vì dependency external library
     * để giữ plugin nhẹ và tương thích với nhiều version Jenkins
     * 
     * @return JSON string representation của object
     */
    public String toJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        
        // Job information
        sb.append("\"jobName\":").append(jsonString(jobName)).append(",");
        sb.append("\"jobFullName\":").append(jsonString(jobFullName)).append(",");
        sb.append("\"jobUrl\":").append(jsonString(jobUrl)).append(",");
        sb.append("\"buildWithParametersUrl\":").append(jsonString(buildWithParametersUrl)).append(",");
        
        // Plugin information
        sb.append("\"activeChoicesPluginAvailable\":").append(activeChoicesPluginAvailable).append(",");
        sb.append("\"activeChoicesPluginVersion\":").append(jsonString(activeChoicesPluginVersion)).append(",");
        
        // Parameters array
        sb.append("\"parameters\":[");
        if (parameters != null) {
            for (int i = 0; i < parameters.size(); i++) {
                sb.append(parameters.get(i).toJson());
                if (i < parameters.size() - 1) {
                    sb.append(",");
                }
            }
        }
        sb.append("]");
        
        sb.append("}");
        return sb.toString();
    }
    
    /**
     * Helper method để escape string cho JSON format
     * 
     * @param str String cần escape
     * @return JSON-safe string hoặc "null" nếu input là null
     */
    private String jsonString(String str) {
        if (str == null) {
            return "null";
        }
        
        // Escape các ký tự đặc biệt cho JSON
        String escaped = str.replace("\\", "\\\\")    // Backslash
                           .replace("\"", "\\\"")     // Double quote
                           .replace("\n", "\\n")      // Newline
                           .replace("\r", "\\r")      // Carriage return
                           .replace("\t", "\\t")      // Tab
                           .replace("\b", "\\b")      // Backspace
                           .replace("\f", "\\f");     // Form feed
        
        return "\"" + escaped + "\"";
    }
    
    /**
     * Lấy số lượng parameters
     * 
     * @return Số lượng parameters trong job
     */
    public int getParameterCount() {
        return parameters != null ? parameters.size() : 0;
    }
    
    /**
     * Kiểm tra xem job có parameters hay không
     * 
     * @return true nếu job có ít nhất 1 parameter, false nếu không
     */
    public boolean hasParameters() {
        return getParameterCount() > 0;
    }
    
    /**
     * Tìm parameter theo tên
     * 
     * @param parameterName Tên parameter cần tìm
     * @return RenderedParameterInfo nếu tìm thấy, null nếu không
     */
    public RenderedParameterInfo findParameterByName(String parameterName) {
        if (parameterName == null || parameters == null) {
            return null;
        }
        
        return parameters.stream()
                .filter(param -> parameterName.equals(param.getName()))
                .findFirst()
                .orElse(null);
    }
    
    @Override
    public String toString() {
        return "RenderedParametersInfo{" +
                "jobName='" + jobName + '\'' +
                ", jobFullName='" + jobFullName + '\'' +
                ", parameterCount=" + getParameterCount() +
                ", activeChoicesPluginAvailable=" + activeChoicesPluginAvailable +
                '}';
    }
}
