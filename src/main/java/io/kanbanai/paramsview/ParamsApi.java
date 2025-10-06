package io.kanbanai.paramsview;

import hudson.Extension;
import hudson.model.*;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.QueryParameter;

import javax.servlet.ServletException;
import java.io.IOException;
import java.util.*;

@Extension
public class ParamsApi implements RootAction {

    @Override
    public String getIconFileName() {
        return null; // Hidden from UI
    }

    @Override
    public String getDisplayName() {
        return "Parameters API";
    }

    @Override
    public String getUrlName() {
        return "params-api";
    }

    // GET /params-api/get?job=jobName
    public void doGet(StaplerRequest req, StaplerResponse rsp, @QueryParameter String job) throws IOException, ServletException {
        if (job == null || job.isEmpty()) {
            rsp.sendError(400, "Missing job parameter");
            return;
        }

        Jenkins jenkins = Jenkins.get();
        Job<?, ?> targetJob = jenkins.getItemByFullName(job, Job.class);
        
        if (targetJob == null) {
            rsp.sendError(404, "Job not found: " + job);
            return;
        }

        try {
            // Check if current user can read the job
            targetJob.checkPermission(Item.READ);
        } catch (Exception e) {
            rsp.sendError(403, "Access denied to job: " + job);
            return;
        }

        JobParametersInfo info = getJobParametersInfo(targetJob);
        
        rsp.setContentType("application/json;charset=UTF-8");
        rsp.getWriter().write(info.toJson());
    }

    // GET /params-api/list - List all jobs with parameters
    public void doList(StaplerRequest req, StaplerResponse rsp) throws IOException {
        Jenkins jenkins = Jenkins.get();
        List<JobInfo> jobs = new ArrayList<>();
        
        for (Item item : jenkins.getAllItems()) {
            if (item instanceof Job) {
                Job<?, ?> job = (Job<?, ?>) item;
                try {
                    job.checkPermission(Item.READ);
                    ParametersDefinitionProperty prop = job.getProperty(ParametersDefinitionProperty.class);
                    if (prop != null && !prop.getParameterDefinitions().isEmpty()) {
                        JobInfo jobInfo = new JobInfo();
                        jobInfo.name = job.getName();
                        jobInfo.fullName = job.getFullName();
                        jobInfo.url = job.getUrl();
                        jobInfo.parametersCount = prop.getParameterDefinitions().size();
                        jobs.add(jobInfo);
                    }
                } catch (Exception e) {
                    // Skip jobs user can't access
                }
            }
        }
        
        rsp.setContentType("application/json;charset=UTF-8");
        rsp.getWriter().write(jobListToJson(jobs));
    }

    private JobParametersInfo getJobParametersInfo(Job<?, ?> job) {
        JobParametersInfo info = new JobParametersInfo();
        info.jobName = job.getName();
        info.jobFullName = job.getFullName();
        info.jobUrl = job.getUrl();
        info.jobDisplayName = job.getDisplayName();
        
        ParametersDefinitionProperty prop = job.getProperty(ParametersDefinitionProperty.class);
        if (prop != null) {
            for (ParameterDefinition def : prop.getParameterDefinitions()) {
                ParameterInfo param = new ParameterInfo();
                param.name = def.getName();
                param.type = def.getClass().getSimpleName();
                param.description = def.getDescription();
                
                // Get default value
                try {
                    ParameterValue defaultValue = def.getDefaultParameterValue();
                    if (defaultValue != null) {
                        param.defaultValue = getParameterValue(defaultValue);
                    }
                } catch (Exception e) {
                    param.defaultValue = null;
                }
                
                // Get choices for choice parameters
                if (def instanceof ChoiceParameterDefinition) {
                    ChoiceParameterDefinition choiceDef = (ChoiceParameterDefinition) def;
                    param.choices = new ArrayList<>(choiceDef.getChoices());
                } else if (def instanceof BooleanParameterDefinition) {
                    param.choices = Arrays.asList("true", "false");
                }
                
                info.parameters.add(param);
            }
        }
        
        return info;
    }

    private Object getParameterValue(ParameterValue pv) {
        try {
            if (pv instanceof StringParameterValue) return ((StringParameterValue) pv).getValue();
            if (pv instanceof BooleanParameterValue) return ((BooleanParameterValue) pv).getValue();
            if (pv instanceof TextParameterValue) return ((TextParameterValue) pv).getValue();
            if (pv instanceof PasswordParameterValue) return "******";
            return pv.getValue();
        } catch (Exception e) {
            return null;
        }
    }

    private String jobListToJson(List<JobInfo> jobs) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"jobs\":[");
        for (int i = 0; i < jobs.size(); i++) {
            JobInfo job = jobs.get(i);
            sb.append("{");
            sb.append("\"name\":").append(json(job.name)).append(",");
            sb.append("\"fullName\":").append(json(job.fullName)).append(",");
            sb.append("\"url\":").append(json(job.url)).append(",");
            sb.append("\"parametersCount\":").append(job.parametersCount);
            sb.append("}");
            if (i < jobs.size() - 1) sb.append(",");
        }
        sb.append("]}");
        return sb.toString();
    }

    public static class JobParametersInfo {
        public String jobName;
        public String jobFullName;
        public String jobUrl;
        public String jobDisplayName;
        public List<ParameterInfo> parameters = new ArrayList<>();
        
        public String toJson() {
            StringBuilder sb = new StringBuilder();
            sb.append("{");
            sb.append("\"jobName\":").append(json(jobName)).append(",");
            sb.append("\"jobFullName\":").append(json(jobFullName)).append(",");
            sb.append("\"jobUrl\":").append(json(jobUrl)).append(",");
            sb.append("\"jobDisplayName\":").append(json(jobDisplayName)).append(",");
            sb.append("\"parameters\":[");
            
            for (int i = 0; i < parameters.size(); i++) {
                ParameterInfo p = parameters.get(i);
                sb.append("{");
                sb.append("\"name\":").append(json(p.name)).append(",");
                sb.append("\"type\":").append(json(p.type)).append(",");
                sb.append("\"description\":").append(json(p.description)).append(",");
                sb.append("\"defaultValue\":").append(jsonObj(p.defaultValue)).append(",");
                sb.append("\"choices\":").append(jsonArray(p.choices));
                sb.append("}");
                if (i < parameters.size() - 1) sb.append(",");
            }
            
            sb.append("]}");
            return sb.toString();
        }
        
        private String json(String s) { 
            return s == null ? "null" : "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r") + "\""; 
        }
        
        private String jsonObj(Object o) { 
            return o == null ? "null" : json(String.valueOf(o)); 
        }
        
        private String jsonArray(List<String> arr) {
            if (arr == null) return "[]";
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < arr.size(); i++) {
                sb.append(json(arr.get(i)));
                if (i < arr.size() - 1) sb.append(",");
            }
            sb.append("]");
            return sb.toString();
        }
    }

    public static class ParameterInfo {
        public String name;
        public String type;
        public String description;
        public Object defaultValue;
        public List<String> choices = new ArrayList<>();
    }

    public static class JobInfo {
        public String name;
        public String fullName;
        public String url;
        public int parametersCount;
    }

    private String json(String s) { 
        return s == null ? "null" : "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""; 
    }
}