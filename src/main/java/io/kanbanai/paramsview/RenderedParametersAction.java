package io.kanbanai.paramsview;

import hudson.Extension;
import hudson.model.*;
import jenkins.model.TransientActionFactory;

import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.Collections;

import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.QueryParameter;

import java.io.IOException;
import java.util.*;

import io.kanbanai.paramsview.service.ParameterRenderingService;
import io.kanbanai.paramsview.service.PluginAvailabilityService;
import io.kanbanai.paramsview.model.RenderedParametersInfo;

/**
 * Jenkins Job Action để thêm endpoint vào mỗi job URL
 *
 * Action này sẽ được inject vào tất cả các Job objects trong Jenkins,
 * cho phép truy cập parameters thông qua URL pattern:
 * {JENKINS_URL}/job/{JOB_NAME}/amt-integration/api?params=param1:value1,param2:value2
 *
 * Hoạt động:
 * - Plugin tạo endpoint /amt-integration/api cho mỗi job
 * - Endpoint /amt-integration/ trả về thông tin hướng dẫn
 * - Endpoint /amt-integration/api?params=... trả về JSON với thông tin parameters
 *
 * Kiến trúc:
 * - Sử dụng TransientActionFactory để inject action vào mỗi job
 * - doIndex() xử lý request tại /amt-integration/ (info endpoint)
 * - doApi() xử lý request tại /amt-integration/api (parameters endpoint)
 * - Trả về JSON response với thông tin parameters
 *
 * @author KanbanAI
 * @since 1.0.3
 */
public class RenderedParametersAction implements Action {

    private final Job<?, ?> job;
    private final ParameterRenderingService parameterService;
    private final PluginAvailabilityService pluginService;

    /**
     * Constructor
     * 
     * @param job Jenkins job instance mà action này được attach vào
     */
    public RenderedParametersAction(Job<?, ?> job) {
        this.job = job;
        this.parameterService = ParameterRenderingService.getInstance();
        this.pluginService = PluginAvailabilityService.getInstance();
    }

    /**
     * {@inheritDoc}
     * 
     * Trả về null để không hiển thị action trong job sidebar.
     */
    @Override
    public String getIconFileName() {
        return null;
    }

    /**
     * {@inheritDoc}
     * 
     * Display name của action (không hiển thị vì iconFileName = null)
     */
    @Override
    public String getDisplayName() {
        return "AMT Parameters";
    }

    /**
     * {@inheritDoc}
     *
     * URL name là "amt-integration" để tạo endpoint: /job/{JOB_NAME}/amt-integration
     * Để hỗ trợ URL pattern: /job/{JOB_NAME}/amt-integration/api?params=...
     */
    @Override
    public String getUrlName() {
        return "amt-integration";
    }

    /**
     * Xử lý GET request tại /job/{JOB_NAME}/amt-integration/api
     * với query parameter "params"
     *
     * URL pattern: /job/{JOB_NAME}/amt-integration/api?params=param1:value1,param2:value2
     *
     * @param req StaplerRequest
     * @param rsp StaplerResponse
     * @throws IOException nếu có lỗi I/O
     */
    public void doApi(StaplerRequest req, StaplerResponse rsp) throws IOException {
        // Hỗ trợ cả GET và POST
        if ("POST".equalsIgnoreCase(req.getMethod())) {
            doApiPost(req, rsp);
            return;
        }
        
        // GET method
        String paramsStr = req.getParameter("params");
        
        if (paramsStr == null) {
            // Không có params parameter - set default to empty
            paramsStr = "";
        }

        try {
            // Check permissions
            if (!checkJobReadPermission(job)) {
                sendErrorResponse(rsp, 403, "Access denied to job: " + job.getName(),
                    "You don't have READ permission for this job");
                return;
            }

            // Parse parameter values từ query string
            Map<String, String> currentValues = parseParameterValues(paramsStr);

            // Delegate to service layer để render parameters
            RenderedParametersInfo parametersInfo = parameterService.renderJobParameters(job, currentValues);

            // Return JSON response
            sendSuccessResponse(rsp, parametersInfo);

        } catch (Exception e) {
            // Log error và return 500
            java.util.logging.Logger.getLogger(getClass().getName())
                .log(java.util.logging.Level.SEVERE, "Unexpected error in doApi: " + e.getMessage(), e);

            sendErrorResponse(rsp, 500, "Internal server error",
                "An unexpected error occurred while processing the request");
        }
    }

    /**
     * Xử lý POST request tại /job/{JOB_NAME}/amt-integration/api
     * với JSON body chứa parameters
     *
     * URL pattern: POST /job/{JOB_NAME}/amt-integration/api
     * Request Body: {"params": "param1:value1,param2:value2"} hoặc {"params": {"param1": "value1", "param2": "value2"}}
     *
     * @param req StaplerRequest
     * @param rsp StaplerResponse
     * @throws IOException nếu có lỗi I/O
     */
    private void doApiPost(StaplerRequest req, StaplerResponse rsp) throws IOException {
        try {
            // Check permissions
            if (!checkJobReadPermission(job)) {
                sendErrorResponse(rsp, 403, "Access denied to job: " + job.getName(),
                    "You don't have READ permission for this job");
                return;
            }

            // Parse JSON body
            Map<String, String> currentValues = parseJsonBody(req);

            // Delegate to service layer để render parameters
            RenderedParametersInfo parametersInfo = parameterService.renderJobParameters(job, currentValues);

            // Return JSON response
            sendSuccessResponse(rsp, parametersInfo);

        } catch (Exception e) {
            // Log error và return 500
            java.util.logging.Logger.getLogger(getClass().getName())
                .log(java.util.logging.Level.SEVERE, "Unexpected error in doApiPost: " + e.getMessage(), e);

            sendErrorResponse(rsp, 500, "Internal server error",
                "An unexpected error occurred while processing the request: " + e.getMessage());
        }
    }

    /**
     * Parse JSON body từ POST request
     * 
     * Hỗ trợ 2 format:
     * 1. String format: {"params": "Channel:C01,depen:[OptionB,OptionA]"}
     * 2. Object format: {"params": {"Channel": "C01", "depen": "[OptionB,OptionA]"}}
     * 
     * @param req StaplerRequest
     * @return Map chứa parameter values
     * @throws IOException nếu có lỗi parse JSON
     */
    private Map<String, String> parseJsonBody(StaplerRequest req) throws IOException {
        try {
            // Read request body
            StringBuilder sb = new StringBuilder();
            java.io.BufferedReader reader = req.getReader();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            
            String body = sb.toString().trim();
            
            if (body.isEmpty()) {
                return new HashMap<>();
            }

            // Simple JSON parsing (không dùng external library)
            // Format 1: {"params": "Channel:C01,depen:[OptionB,OptionA]"}
            // Format 2: {"params": {"Channel": "C01", "depen": "[OptionB,OptionA]"}}
            
            // Extract params value
            int paramsStart = body.indexOf("\"params\"");
            if (paramsStart == -1) {
                return new HashMap<>();
            }
            
            int colonAfterParams = body.indexOf(":", paramsStart);
            if (colonAfterParams == -1) {
                return new HashMap<>();
            }
            
            // Skip whitespace after colon
            int valueStart = colonAfterParams + 1;
            while (valueStart < body.length() && Character.isWhitespace(body.charAt(valueStart))) {
                valueStart++;
            }
            
            if (valueStart >= body.length()) {
                return new HashMap<>();
            }
            
            // Check if value is string or object
            if (body.charAt(valueStart) == '"') {
                // Format 1: String value
                int stringStart = valueStart + 1;
                int stringEnd = body.indexOf('"', stringStart);
                
                // Handle escaped quotes
                while (stringEnd > 0 && body.charAt(stringEnd - 1) == '\\') {
                    stringEnd = body.indexOf('"', stringEnd + 1);
                }
                
                if (stringEnd == -1) {
                    return new HashMap<>();
                }
                
                String paramsStr = body.substring(stringStart, stringEnd);
                // Unescape the string
                paramsStr = unescapeJsonString(paramsStr);
                return parseParameterValues(paramsStr);
                
            } else if (body.charAt(valueStart) == '{') {
                // Format 2: Object value
                int objectStart = valueStart;
                int objectEnd = findMatchingBrace(body, objectStart);
                
                if (objectEnd == -1) {
                    return new HashMap<>();
                }
                
                String paramsObject = body.substring(objectStart, objectEnd + 1);
                return parseJsonObject(paramsObject);
            }
            
            return new HashMap<>();
            
        } catch (Exception e) {
            java.util.logging.Logger.getLogger(getClass().getName())
                .log(java.util.logging.Level.WARNING, "Error parsing JSON body: " + e.getMessage(), e);
            return new HashMap<>();
        }
    }

    /**
     * Parse JSON object thành Map
     * Format: {"param1": "value1", "param2": "value2", "param3": "[v1,v2,v3]"}
     */
    private Map<String, String> parseJsonObject(String jsonObject) {
        Map<String, String> result = new HashMap<>();
        
        try {
            // Remove leading/trailing { }
            String content = jsonObject.trim();
            if (content.startsWith("{")) {
                content = content.substring(1);
            }
            if (content.endsWith("}")) {
                content = content.substring(0, content.length() - 1);
            }
            content = content.trim();
            
            if (content.isEmpty()) {
                return result;
            }
            
            // Parse each key-value pair
            int i = 0;
            while (i < content.length()) {
                // Skip whitespace
                while (i < content.length() && Character.isWhitespace(content.charAt(i))) {
                    i++;
                }
                
                if (i >= content.length()) break;
                
                // Parse key
                if (content.charAt(i) != '"') {
                    break;
                }
                
                int keyStart = i + 1;
                int keyEnd = content.indexOf('"', keyStart);
                if (keyEnd == -1) break;
                
                String key = content.substring(keyStart, keyEnd);
                
                // Find colon
                i = keyEnd + 1;
                while (i < content.length() && content.charAt(i) != ':') {
                    i++;
                }
                if (i >= content.length()) break;
                
                i++; // Skip colon
                
                // Skip whitespace
                while (i < content.length() && Character.isWhitespace(content.charAt(i))) {
                    i++;
                }
                
                if (i >= content.length()) break;
                
                // Parse value
                String value;
                if (content.charAt(i) == '"') {
                    // String value
                    int valueStart = i + 1;
                    int valueEnd = content.indexOf('"', valueStart);
                    
                    // Handle escaped quotes
                    while (valueEnd > 0 && content.charAt(valueEnd - 1) == '\\') {
                        valueEnd = content.indexOf('"', valueEnd + 1);
                    }
                    
                    if (valueEnd == -1) break;
                    
                    value = content.substring(valueStart, valueEnd);
                    value = unescapeJsonString(value);
                    i = valueEnd + 1;
                } else if (content.charAt(i) == '[') {
                    // Array value
                    int arrayEnd = content.indexOf(']', i);
                    if (arrayEnd == -1) break;
                    
                    value = content.substring(i, arrayEnd + 1);
                    i = arrayEnd + 1;
                } else {
                    // Other value (number, boolean, null)
                    int valueEnd = i;
                    while (valueEnd < content.length() && 
                           content.charAt(valueEnd) != ',' && 
                           content.charAt(valueEnd) != '}') {
                        valueEnd++;
                    }
                    value = content.substring(i, valueEnd).trim();
                    i = valueEnd;
                }
                
                result.put(key, value);
                
                // Skip comma
                while (i < content.length() && (Character.isWhitespace(content.charAt(i)) || content.charAt(i) == ',')) {
                    i++;
                }
            }
            
        } catch (Exception e) {
            java.util.logging.Logger.getLogger(getClass().getName())
                .log(java.util.logging.Level.WARNING, "Error parsing JSON object: " + e.getMessage());
        }
        
        return result;
    }

    /**
     * Find matching closing brace
     */
    private int findMatchingBrace(String str, int openBraceIndex) {
        int depth = 0;
        for (int i = openBraceIndex; i < str.length(); i++) {
            char c = str.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    /**
     * Unescape JSON string
     */
    private String unescapeJsonString(String str) {
        return str.replace("\\\"", "\"")
                  .replace("\\\\", "\\")
                  .replace("\\n", "\n")
                  .replace("\\r", "\r")
                  .replace("\\t", "\t")
                  .replace("\\b", "\b")
                  .replace("\\f", "\f");
    }

    /**
     * Xử lý request tại /job/{JOB_NAME}/amt-integration/
     * Trả về thông tin hướng dẫn sử dụng API
     *
     * @param req StaplerRequest
     * @param rsp StaplerResponse
     * @throws IOException nếu có lỗi I/O
     */
    public void doIndex(StaplerRequest req, StaplerResponse rsp) throws IOException {
        rsp.setStatus(200);
        rsp.setContentType("application/json;charset=UTF-8");

        StringBuilder json = new StringBuilder();
        json.append("{");
        json.append("\"success\":true,");
        json.append("\"message\":\"AMT Parameters API\",");
        json.append("\"usage\":\"Use /job/").append(job.getFullName()).append("/amt-integration/api?params=param1:value1,param2:value2\",");
        json.append("\"jobName\":").append(jsonString(job.getFullName())).append(",");
        json.append("\"endpoints\":{");
        json.append("\"parameters\":\"").append(req.getRequestURL()).append("api?params=param1:value1,param2:value2\"");
        json.append("}");
        json.append("}");

        rsp.getWriter().write(json.toString());
    }

    /**
     * Check READ permission cho job
     */
    private boolean checkJobReadPermission(Job<?, ?> job) {
        try {
            job.checkPermission(Item.READ);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Parse parameter values từ query string
     * 
     * Format hỗ trợ:
     * - Single value: "param1:value1,param2:value2"
     * - Array value (checkbox): "param1:value1,param2:[value1,value2,value3]"
     * 
     * Ví dụ: "Channel:C01,depen:[OptionB,OptionA]"
     * Kết quả: {Channel="C01", depen="OptionB,OptionA"}
     */
    private Map<String, String> parseParameterValues(String paramsStr) {
        Map<String, String> values = new HashMap<>();

        if (paramsStr == null || paramsStr.trim().isEmpty()) {
            return values;
        }

        try {
            // Parse từng parameter, xử lý cả single value và array value
            int i = 0;
            while (i < paramsStr.length()) {
                // Tìm tên parameter (trước dấu :)
                int colonIndex = paramsStr.indexOf(':', i);
                if (colonIndex == -1) {
                    break; // Không còn parameter nào
                }
                
                String key = paramsStr.substring(i, colonIndex).trim();
                
                // Bỏ qua leading comma nếu có
                if (key.startsWith(",")) {
                    key = key.substring(1).trim();
                }
                
                if (key.isEmpty()) {
                    i = colonIndex + 1;
                    continue;
                }
                
                // Parse value (có thể là single value hoặc array [value1,value2])
                int valueStart = colonIndex + 1;
                String value;
                
                // Check xem value có phải là array không (bắt đầu bằng [)
                if (valueStart < paramsStr.length() && paramsStr.charAt(valueStart) == '[') {
                    // Parse array value: [value1,value2,value3]
                    int closeBracket = paramsStr.indexOf(']', valueStart);
                    if (closeBracket == -1) {
                        // Không tìm thấy ], treat như string thông thường
                        int nextComma = paramsStr.indexOf(',', valueStart);
                        if (nextComma == -1) {
                            value = paramsStr.substring(valueStart).trim();
                            i = paramsStr.length();
                        } else {
                            value = paramsStr.substring(valueStart, nextComma).trim();
                            i = nextComma + 1;
                        }
                    } else {
                        // Extract array content (bỏ [])
                        String arrayContent = paramsStr.substring(valueStart + 1, closeBracket).trim();
                        // Convert array thành comma-separated string
                        value = arrayContent;
                        i = closeBracket + 1;
                        
                        // Skip comma sau ] nếu có
                        if (i < paramsStr.length() && paramsStr.charAt(i) == ',') {
                            i++;
                        }
                    }
                } else {
                    // Parse single value (đến dấu , tiếp theo hoặc end of string)
                    int nextComma = paramsStr.indexOf(',', valueStart);
                    
                    // Tuy nhiên, cần check xem comma có nằm trong array bracket không
                    int openBracket = paramsStr.indexOf('[', valueStart);
                    if (openBracket != -1 && openBracket < nextComma) {
                        // Có array bracket giữa value và comma, tìm comma sau ]
                        int closeBracket = paramsStr.indexOf(']', openBracket);
                        if (closeBracket != -1) {
                            nextComma = paramsStr.indexOf(',', closeBracket);
                        }
                    }
                    
                    if (nextComma == -1) {
                        value = paramsStr.substring(valueStart).trim();
                        i = paramsStr.length();
                    } else {
                        value = paramsStr.substring(valueStart, nextComma).trim();
                        i = nextComma + 1;
                    }
                }
                
                // Add to map
                if (!key.isEmpty()) {
                    values.put(key, value);
                }
            }
            
        } catch (Exception e) {
            java.util.logging.Logger.getLogger(getClass().getName())
                .log(java.util.logging.Level.WARNING, "Error parsing parameter values: " + paramsStr + " - " + e.getMessage());
        }

        return values;
    }

    /**
     * Send error response
     */
    private void sendErrorResponse(StaplerResponse rsp, int statusCode, String error, String details) throws IOException {
        rsp.setStatus(statusCode);
        rsp.setContentType("application/json;charset=UTF-8");

        StringBuilder json = new StringBuilder();
        json.append("{");
        json.append("\"success\":false,");
        json.append("\"error\":").append(jsonString(error)).append(",");
        json.append("\"details\":").append(jsonString(details)).append(",");
        json.append("\"statusCode\":").append(statusCode);
        json.append("}");

        rsp.getWriter().write(json.toString());
    }

    /**
     * Send success response
     */
    private void sendSuccessResponse(StaplerResponse rsp, RenderedParametersInfo parametersInfo) throws IOException {
        rsp.setStatus(200);
        rsp.setContentType("application/json;charset=UTF-8");

        StringBuilder json = new StringBuilder();
        json.append("{");
        json.append("\"success\":true,");
        json.append("\"data\":").append(parametersInfo.toJson());
        json.append("}");

        rsp.getWriter().write(json.toString());
    }

    /**
     * Escape string cho JSON
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
     * Factory để inject action vào tất cả Job objects
     */
    @Extension
    public static class Factory extends TransientActionFactory<Job> {

        @Override
        public Class<Job> type() {
            return Job.class;
        }

        @Nonnull
        @Override
        public Collection<? extends Action> createFor(@Nonnull Job target) {
            // Chỉ inject action cho jobs có parameters
            // Kiểm tra xem job có ParametersDefinitionProperty không
            if (target.getProperty(ParametersDefinitionProperty.class) != null) {
                return Collections.singleton(new RenderedParametersAction(target));
            }
            return Collections.emptyList();
        }
    }
}
