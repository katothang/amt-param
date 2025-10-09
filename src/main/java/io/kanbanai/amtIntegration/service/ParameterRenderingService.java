package io.kanbanai.amtIntegration.service;

import hudson.model.*;
import io.kanbanai.amtIntegration.model.RenderedParameterInfo;
import io.kanbanai.amtIntegration.model.RenderedParametersInfo;
import io.kanbanai.amtIntegration.model.ParameterInputType;
import io.kanbanai.amtIntegration.config.MessageConstants;
import io.kanbanai.amtIntegration.util.ValidationUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * Service responsible for rendering Jenkins parameters into display information.
 *
 * This service handles the conversion of Jenkins ParameterDefinition objects
 * into RenderedParameterInfo with complete information needed for UI display,
 * including both built-in parameters and Active Choices parameters.
 *
 * The service is designed to operate independently of the Active Choices Plugin,
 * with graceful fallback when the plugin is not available.
 *
 * Flow:
 * 1. Receives job and current parameter values
 * 2. Extracts all parameter definitions from the job
 * 3. Renders each parameter with appropriate type and choices
 * 4. Handles Active Choices parameters with dynamic content
 * 5. Returns comprehensive parameter information for API response
 *
 * @author KanbanAI
 * @since 1.0.2
 */
public class ParameterRenderingService {
    
    private static final Logger LOGGER = Logger.getLogger(ParameterRenderingService.class.getName());
    
    // Singleton instance
    private static ParameterRenderingService instance;
    
    // Dependencies
    private final PluginAvailabilityService pluginService;
    private final ActiveChoicesService activeChoicesService;
    
    /**
     * Private constructor for Singleton pattern
     */
    private ParameterRenderingService() {
        this.pluginService = PluginAvailabilityService.getInstance();
        this.activeChoicesService = ActiveChoicesService.getInstance();
    }
    
    /**
     * Lấy instance duy nhất của service (Singleton pattern)
     * 
     * @return ParameterRenderingService instance
     */
    public static synchronized ParameterRenderingService getInstance() {
        if (instance == null) {
            instance = new ParameterRenderingService();
        }
        return instance;
    }
    
    /**
     * Renders all parameters of a Jenkins job
     *
     * This method retrieves all ParameterDefinition from the job and converts
     * them into RenderedParametersInfo with complete information for display.
     *
     * @param job Jenkins job to render parameters for
     * @param currentValues Map of current parameter values (for cascade parameters)
     * @return RenderedParametersInfo containing complete information of all parameters
     */
    public RenderedParametersInfo renderJobParameters(Job<?, ?> job, Map<String, String> currentValues) {
        ValidationUtils.validateJob(job);

        LOGGER.log(Level.INFO, String.format(MessageConstants.INFO_STARTING_PARAMETER_RENDERING, job.getFullName()));
        
        // Create response object
        RenderedParametersInfo info = new RenderedParametersInfo(
            job.getName(),
            job.getFullName(),
            job.getUrl()
        );

        // Set plugin availability information
        boolean activeChoicesAvailable = pluginService.isActiveChoicesPluginAvailable();
        info.setActiveChoicesPluginAvailable(activeChoicesAvailable);
        info.setActiveChoicesPluginVersion(pluginService.getActiveChoicesPluginVersion());

        try {
            // Get ParametersDefinitionProperty from job
            ParametersDefinitionProperty paramsProp = job.getProperty(ParametersDefinitionProperty.class);

            if (paramsProp == null) {
                LOGGER.log(Level.INFO, "Job " + job.getFullName() + " has no parameters");
                return info;
            }

            List<ParameterDefinition> paramDefs = paramsProp.getParameterDefinitions();
            if (paramDefs == null || paramDefs.isEmpty()) {
                LOGGER.log(Level.INFO, "Job " + job.getFullName() + " has ParametersDefinitionProperty but no parameter definitions");
                return info;
            }

            LOGGER.log(Level.INFO, String.format(MessageConstants.INFO_PARAMETERS_FOUND, paramDefs.size(), job.getFullName()));

            // Render each parameter
            for (ParameterDefinition paramDef : paramDefs) {
                try {
                    RenderedParameterInfo paramInfo = renderSingleParameter(paramDef, job, currentValues);
                    info.addParameter(paramInfo);

                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, String.format(MessageConstants.WARNING_PARAMETER_RENDER_ERROR, paramDef.getName(), e.getMessage()), e);

                    // Create parameter info with error message instead of skipping
                    RenderedParameterInfo errorParam = createErrorParameter(paramDef, e.getMessage());
                    info.addParameter(errorParam);
                }
            }

            LOGGER.log(Level.INFO, String.format(MessageConstants.INFO_PARAMETER_RENDERING_COMPLETE, info.getParameterCount(), job.getFullName()));
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, String.format(MessageConstants.ERROR_CRITICAL_PARAMETER_RENDERING, job.getFullName(), e.getMessage()), e);
            throw new RuntimeException(String.format(MessageConstants.ERROR_CANNOT_RENDER_PARAMETERS, job.getFullName()), e);
        }
        
        return info;
    }
    
    /**
     * Renders a single parameter definition into RenderedParameterInfo
     *
     * @param paramDef ParameterDefinition to render
     * @param job Job containing the parameter (for context)
     * @param currentValues Map of current parameter values
     * @return Rendered RenderedParameterInfo
     */
    public RenderedParameterInfo renderSingleParameter(ParameterDefinition paramDef, Job<?, ?> job, Map<String, String> currentValues) {
        ValidationUtils.validateParameterDefinition(paramDef);

        LOGGER.log(Level.FINE, String.format(MessageConstants.DEBUG_RENDERING_PARAMETER, paramDef.getName(), paramDef.getClass().getSimpleName()));
        
        // Tạo base parameter info
        RenderedParameterInfo paramInfo = new RenderedParameterInfo(
            paramDef.getName(),
            paramDef.getClass().getSimpleName(),
            paramDef.getDescription()
        );
        
        // Set current value
        String currentValue = getCurrentParameterValue(paramDef, currentValues);
        paramInfo.setCurrentValue(currentValue);
        
        // Determine input type
        ParameterInputType inputType = ParameterInputType.fromParameterClassName(paramDef.getClass().getName());
        paramInfo.setInputType(inputType);
        
        // Render based on parameter type
        if (isBuiltInParameter(paramDef)) {
            renderBuiltInParameter(paramDef, paramInfo);
        } else if (isActiveChoicesParameter(paramDef)) {
            renderActiveChoicesParameter(paramDef, paramInfo, currentValues);
        } else {
            // Unknown parameter type - treat as text input
            LOGGER.log(Level.WARNING, "Unknown parameter type: " + paramDef.getClass().getName() + " - treating as text input");
            paramInfo.setInputType(ParameterInputType.TEXT);
        }
        
        return paramInfo;
    }
    
    /**
     * Lấy giá trị hiện tại của parameter
     * Ưu tiên: currentValues -> default value -> null
     */
    private String getCurrentParameterValue(ParameterDefinition paramDef, Map<String, String> currentValues) {
        // Ưu tiên giá trị từ currentValues (từ request)
        if (currentValues != null) {
            String currentValue = currentValues.get(paramDef.getName());
            if (currentValue != null) {
                return currentValue;
            }
        }
        
        // Fallback to default value
        try {
            ParameterValue defaultValue = paramDef.getDefaultParameterValue();
            if (defaultValue != null) {
                return getParameterValueAsString(defaultValue);
            }
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Không thể lấy default value cho parameter " + paramDef.getName() + ": " + e.getMessage());
        }
        
        return null;
    }
    
    /**
     * Kiểm tra xem parameter có phải là built-in Jenkins parameter không
     */
    private boolean isBuiltInParameter(ParameterDefinition paramDef) {
        String className = paramDef.getClass().getName();
        return className.startsWith("hudson.model.") || 
               className.contains("StringParameterDefinition") ||
               className.contains("BooleanParameterDefinition") ||
               className.contains("ChoiceParameterDefinition") ||
               className.contains("TextParameterDefinition") ||
               className.contains("PasswordParameterDefinition") ||
               className.contains("FileParameterDefinition");
    }
    
    /**
     * Kiểm tra xem parameter có phải là Active Choices parameter không
     */
    private boolean isActiveChoicesParameter(ParameterDefinition paramDef) {
        String className = paramDef.getClass().getName();
        return className.contains("org.biouno.unochoice") ||
               className.contains("ChoiceParameter") ||
               className.contains("CascadeChoiceParameter") ||
               className.contains("DynamicReferenceParameter");
    }
    
    /**
     * Render built-in Jenkins parameters
     */
    private void renderBuiltInParameter(ParameterDefinition paramDef, RenderedParameterInfo paramInfo) {
        try {
            if (paramDef instanceof ChoiceParameterDefinition) {
                // Choice Parameter: dropdown với các lựa chọn cố định
                ChoiceParameterDefinition choiceDef = (ChoiceParameterDefinition) paramDef;
                paramInfo.setChoices(new ArrayList<>(choiceDef.getChoices()));
                paramInfo.setInputType(ParameterInputType.SELECT);
                
            } else if (paramDef instanceof BooleanParameterDefinition) {
                // Boolean Parameter: checkbox true/false
                List<String> boolChoices = new ArrayList<>();
                boolChoices.add("true");
                boolChoices.add("false");
                paramInfo.setChoices(boolChoices);
                paramInfo.setInputType(ParameterInputType.CHECKBOX);
                
            } else if (paramDef instanceof TextParameterDefinition) {
                // Text Parameter: textarea cho text nhiều dòng
                paramInfo.setInputType(ParameterInputType.TEXTAREA);
                
            } else if (paramDef instanceof PasswordParameterDefinition) {
                // Password Parameter: input password
                paramInfo.setInputType(ParameterInputType.PASSWORD);
                
            } else if (paramDef instanceof StringParameterDefinition) {
                // String Parameter: input text đơn giản
                paramInfo.setInputType(ParameterInputType.TEXT);
                
            } else {
                // Other built-in types
                paramInfo.setInputType(ParameterInputType.TEXT);
            }
            
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Lỗi khi render built-in parameter " + paramDef.getName() + ": " + e.getMessage());
            paramInfo.setErrorMessage("Lỗi khi render parameter: " + e.getMessage());
        }
    }
    
    /**
     * Render Active Choices parameters
     */
    private void renderActiveChoicesParameter(ParameterDefinition paramDef, RenderedParameterInfo paramInfo, Map<String, String> currentValues) {
        paramInfo.setDynamic(true);
        
        if (!pluginService.isActiveChoicesPluginAvailable()) {
            // Active Choices plugin không khả dụng - fallback
            LOGGER.log(Level.INFO, "Active Choices plugin không khả dụng cho parameter " + paramDef.getName() + " - sử dụng fallback");
            paramInfo.setInputType(ParameterInputType.TEXT);
            paramInfo.setErrorMessage("Active Choices plugin không khả dụng - parameter được hiển thị như text input");
            return;
        }
        
        try {
            // Delegate to ActiveChoicesService
            activeChoicesService.renderActiveChoicesParameter(paramDef, paramInfo, currentValues);
            
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Lỗi khi render Active Choices parameter " + paramDef.getName() + ": " + e.getMessage());
            
            // Fallback to text input
            paramInfo.setInputType(ParameterInputType.TEXT);
            paramInfo.setErrorMessage("Lỗi khi render Active Choices parameter: " + e.getMessage());
        }
    }
    
    /**
     * Tạo parameter info cho trường hợp có lỗi
     */
    private RenderedParameterInfo createErrorParameter(ParameterDefinition paramDef, String errorMessage) {
        RenderedParameterInfo errorParam = new RenderedParameterInfo(
            paramDef.getName(),
            paramDef.getClass().getSimpleName(),
            paramDef.getDescription()
        );
        
        errorParam.setInputType(ParameterInputType.TEXT);
        errorParam.setErrorMessage(errorMessage);
        
        return errorParam;
    }
    
    /**
     * Chuyển đổi ParameterValue thành String
     */
    private String getParameterValueAsString(ParameterValue pv) {
        try {
            if (pv instanceof StringParameterValue) {
                return ((StringParameterValue) pv).getValue();
            }
            if (pv instanceof BooleanParameterValue) {
                return String.valueOf(((BooleanParameterValue) pv).getValue());
            }
            if (pv instanceof TextParameterValue) {
                return ((TextParameterValue) pv).getValue();
            }
            if (pv instanceof PasswordParameterValue) {
                // Không trả về password value vì lý do bảo mật
                return "";
            }
            
            // Generic fallback
            Object value = pv.getValue();
            return value != null ? value.toString() : "";
            
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Lỗi khi convert ParameterValue to String: " + e.getMessage());
            return "";
        }
    }
}
