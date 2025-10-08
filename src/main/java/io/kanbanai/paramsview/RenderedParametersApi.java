package io.kanbanai.paramsview;

import hudson.Extension;
import hudson.model.*;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.ServletException;
import java.io.IOException;
import java.util.*;

import io.kanbanai.paramsview.service.ParameterRenderingService;
import io.kanbanai.paramsview.service.PluginAvailabilityService;
import io.kanbanai.paramsview.model.RenderedParametersInfo;

/**
 * Jenkins Plugin REST API Controller để lấy thông tin parameters của job
 *
 * Controller này cung cấp REST API endpoint để lấy thông tin đầy đủ về các parameters
 * của một Jenkins job, tương tự như màn hình "Build with Parameters" của Jenkins UI.
 *
 * Kiến trúc mới (v1.0.2+):
 * - Controller chỉ xử lý HTTP request/response và validation
 * - Business logic được delegate cho các Service classes
 * - Hỗ trợ graceful fallback khi Active Choices plugin không khả dụng
 * - Sử dụng typed models thay vì generic Objects
 *
 * Các tính năng chính:
 * - Lấy tất cả parameters của job (built-in và Active Choices)
 * - Render dynamic parameters với giá trị thực tế
 * - Xử lý cascade parameters (parameters phụ thuộc vào nhau)
 * - Hỗ trợ tất cả loại parameter: String, Boolean, Choice, Text, Password, Active Choices, etc.
 * - Kiểm tra plugin availability trước khi sử dụng
 *
 * @author KanbanAI
 * @since 1.0.2
 */
@Extension
public class RenderedParametersApi implements RootAction {

    // Dependencies - sử dụng Singleton pattern cho lightweight DI
    private final ParameterRenderingService parameterService;
    private final PluginAvailabilityService pluginService;

    /**
     * Constructor - khởi tạo service dependencies
     *
     * Sử dụng Singleton pattern để lightweight dependency injection.
     * Services được khởi tạo lazy và cached để tối ưu performance.
     */
    public RenderedParametersApi() {
        this.parameterService = ParameterRenderingService.getInstance();
        this.pluginService = PluginAvailabilityService.getInstance();
    }

    /**
     * {@inheritDoc}
     *
     * Trả về null để không hiển thị plugin trong Jenkins sidebar menu.
     * Plugin chỉ cung cấp REST API, không có UI interface.
     *
     * @return null - không hiển thị icon trong sidebar
     */
    @Override
    public String getIconFileName() {
        return null;
    }

    /**
     * {@inheritDoc}
     *
     * Display name của plugin, sử dụng cho logging và debugging.
     *
     * @return Display name của plugin
     */
    @Override
    public String getDisplayName() {
        return "AMT Parameters API";
    }

    /**
     * {@inheritDoc}
     *
     * URL name định nghĩa endpoint path cho plugin.
     * API sẽ accessible tại: {JENKINS_URL}/amt-param/
     *
     * @return URL path segment cho plugin
     */
    @Override
    public String getUrlName() {
        return "amt-integration";
    }

    /**
     * REST API endpoint để lấy thông tin parameters của một job
     *
     * Endpoint này đã được refactor để sử dụng service layer architecture,
     * cung cấp better separation of concerns và error handling.
     *
     * Cách sử dụng:
     * GET /amt-param/get?job=https://jenkins.example.com/job/jobName/&params=param1:value1,param2:value2
     *
     * Query Parameters:
     * - job (required): URL của Jenkins job
     * - params (optional): Current parameter values cho cascade parameters, format: "param1:value1,param2:value2"
     *
     * Response: JSON object chứa thông tin job và tất cả parameters đã được render
     *
     * @param req StaplerRequest chứa query parameters
     * @param rsp StaplerResponse để trả về kết quả JSON
     * @throws IOException Nếu có lỗi I/O
     * @throws ServletException Nếu có lỗi servlet
     */
    public void doGet(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        try {
            // 1. Validate và extract job URL
            String jobUrl = req.getParameter("job");
            if (jobUrl == null || jobUrl.trim().isEmpty()) {
                sendErrorResponse(rsp, 400, "Job parameter is required",
                    "Usage: GET /amt-param/get?job=https://jenkins.example.com/job/jobName/");
                return;
            }

            // 2. Parse job name từ URL
            String jobName = extractJobNameFromUrl(jobUrl);
            if (jobName == null || jobName.trim().isEmpty()) {
                sendErrorResponse(rsp, 400, "Invalid job URL format",
                    "Expected format: https://jenkins.example.com/job/jobName/ or https://jenkins.example.com/job/folder/job/jobName/");
                return;
            }

            // 3. Find và validate job
            Job<?, ?> job = findAndValidateJob(jobName);
            if (job == null) {
                sendErrorResponse(rsp, 404, "Job not found: " + jobName,
                    "Please check the job name and ensure it exists in Jenkins");
                return;
            }

            // 4. Check permissions
            if (!checkJobReadPermission(job)) {
                sendErrorResponse(rsp, 403, "Access denied to job: " + jobName,
                    "You don't have READ permission for this job");
                return;
            }

            // 5. Parse parameter values từ query string
            Map<String, String> currentValues = parseParameterValues(req.getParameter("params"));

            // 6. Delegate to service layer để render parameters
            RenderedParametersInfo parametersInfo = parameterService.renderJobParameters(job, currentValues);

            // 7. Return JSON response
            sendSuccessResponse(rsp, parametersInfo);

        } catch (Exception e) {
            // Log error và return 500
            java.util.logging.Logger.getLogger(getClass().getName())
                .log(java.util.logging.Level.SEVERE, "Unexpected error in doGet: " + e.getMessage(), e);

            sendErrorResponse(rsp, 500, "Internal server error",
                "An unexpected error occurred while processing the request");
        }
    }

    /**
     * Helper method để send error response với consistent format
     *
     * Tạo standardized error response format cho tất cả error cases.
     * Response format:
     * {
     *   "success": false,
     *   "error": "Error message",
     *   "details": "Detailed description",
     *   "statusCode": 400
     * }
     *
     * @param rsp StaplerResponse object để write response
     * @param statusCode HTTP status code (400, 403, 404, 500, etc.)
     * @param error Short error message
     * @param details Detailed error description for debugging
     * @throws IOException nếu có lỗi khi write response
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
     * Helper method để send success response với consistent format
     *
     * Tạo standardized success response format.
     * Response format:
     * {
     *   "success": true,
     *   "data": { ... RenderedParametersInfo ... }
     * }
     *
     * @param rsp StaplerResponse object để write response
     * @param parametersInfo RenderedParametersInfo object chứa job parameters data
     * @throws IOException nếu có lỗi khi write response
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
     * Find và validate Jenkins job by name
     *
     * Tìm job trong Jenkins instance sử dụng full name (bao gồm folder path).
     * Method này handle các edge cases như Jenkins instance không khả dụng.
     *
     * @param jobName Full job name (có thể bao gồm folder path, ví dụ: "folder/jobName")
     * @return Job instance nếu tìm thấy, null nếu không tìm thấy hoặc có lỗi
     */
    private Job<?, ?> findAndValidateJob(String jobName) {
        try {
            Jenkins jenkins = Jenkins.get();
            if (jenkins == null) {
                java.util.logging.Logger.getLogger(getClass().getName())
                    .log(java.util.logging.Level.WARNING, "Jenkins instance is not available");
                return null;
            }

            return jenkins.getItemByFullName(jobName, Job.class);

        } catch (Exception e) {
            java.util.logging.Logger.getLogger(getClass().getName())
                .log(java.util.logging.Level.WARNING, "Error finding job " + jobName + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * Check READ permission cho job
     *
     * Kiểm tra xem current user có quyền READ job hay không.
     * Sử dụng Jenkins security framework để check permission.
     *
     * @param job Job instance cần check permission
     * @return true nếu có READ permission, false nếu không có permission
     */
    private boolean checkJobReadPermission(Job<?, ?> job) {
        try {
            job.checkPermission(Item.READ);
            return true;
        } catch (Exception e) {
            // AccessDeniedException hoặc các security exceptions khác
            return false;
        }
    }

    /**
     * Extract job name từ Jenkins URL
     *
     * Hỗ trợ các format:
     * - https://jenkins.example.com/job/jobName/ -> jobName
     * - https://jenkins.example.com/job/folder/job/jobName/ -> folder/jobName
     * - https://jenkins.example.com/job/folder1/job/folder2/job/jobName/ -> folder1/folder2/jobName
     *
     * @param jobUrl Jenkins job URL
     * @return Job name hoặc null nếu URL không hợp lệ
     */
    private String extractJobNameFromUrl(String jobUrl) {
        if (jobUrl == null || jobUrl.trim().isEmpty()) {
            return null;
        }

        try {
            // Normalize URL - remove trailing slash
            String url = jobUrl.trim();
            if (url.endsWith("/")) {
                url = url.substring(0, url.length() - 1);
            }

            // Parse job name từ URL pattern: .../job/name/job/name/...
            String[] parts = url.split("/job/");
            if (parts.length < 2) {
                return null;
            }

            // Join tất cả các phần sau /job/ bằng /
            StringBuilder jobName = new StringBuilder();
            for (int i = 1; i < parts.length; i++) {
                if (i > 1) {
                    jobName.append("/");
                }
                jobName.append(parts[i]);
            }

            return jobName.toString();

        } catch (Exception e) {
            java.util.logging.Logger.getLogger(getClass().getName())
                .log(java.util.logging.Level.WARNING, "Error parsing job URL: " + jobUrl + " - " + e.getMessage());
            return null;
        }
    }

    /**
     * Parse parameter values từ query string
     *
     * Format hỗ trợ: "param1:value1,param2:value2,param3:value with spaces"
     *
     * @param paramsStr Query string chứa parameter values
     * @return Map<String, String> với key là tên parameter, value là giá trị
     */
    private Map<String, String> parseParameterValues(String paramsStr) {
        Map<String, String> values = new HashMap<>();

        if (paramsStr == null || paramsStr.trim().isEmpty()) {
            return values;
        }

        try {
            // Split by comma, nhưng cẩn thận với values có chứa comma
            String[] pairs = paramsStr.split(",");
            for (String pair : pairs) {
                if (pair.trim().isEmpty()) {
                    continue;
                }

                // Split by first colon only (values có thể chứa colon)
                int colonIndex = pair.indexOf(':');
                if (colonIndex > 0 && colonIndex < pair.length() - 1) {
                    String key = pair.substring(0, colonIndex).trim();
                    String value = pair.substring(colonIndex + 1).trim();

                    if (!key.isEmpty()) {
                        values.put(key, value);
                    }
                }
            }
        } catch (Exception e) {
            java.util.logging.Logger.getLogger(getClass().getName())
                .log(java.util.logging.Level.WARNING, "Error parsing parameter values: " + paramsStr + " - " + e.getMessage());
        }

        return values;
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
}