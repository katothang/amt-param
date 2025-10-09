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
     * Xử lý request tại /job/{JOB_NAME}/amt-integration/api
     * với query parameter "params"
     *
     * URL pattern: /job/{JOB_NAME}/amt-integration/api?params=param1:value1,param2:value2
     *
     * @param req StaplerRequest
     * @param rsp StaplerResponse
     * @throws IOException nếu có lỗi I/O
     */
    public void doApi(StaplerRequest req, StaplerResponse rsp) throws IOException {
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
                .log(java.util.logging.Level.SEVERE, "Unexpected error in doIndex: " + e.getMessage(), e);

            sendErrorResponse(rsp, 500, "Internal server error",
                "An unexpected error occurred while processing the request");
        }
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
     * Format: "param1:value1,param2:value2"
     */
    private Map<String, String> parseParameterValues(String paramsStr) {
        Map<String, String> values = new HashMap<>();

        if (paramsStr == null || paramsStr.trim().isEmpty()) {
            return values;
        }

        try {
            String[] pairs = paramsStr.split(",");
            for (String pair : pairs) {
                if (pair.trim().isEmpty()) {
                    continue;
                }

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
                .log(java.util.logging.Level.WARNING, "Error parsing parameter values: " + paramsStr);
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
