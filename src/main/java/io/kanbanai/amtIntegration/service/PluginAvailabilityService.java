package io.kanbanai.amtIntegration.service;

import hudson.PluginWrapper;
import jenkins.model.Jenkins;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * Service for checking availability of Jenkins plugins.
 *
 * This service provides methods to check whether a plugin is installed
 * and activated before using features of that plugin.
 *
 * This ensures that code will work stably even when dependent plugins
 * are not installed, instead of causing ClassNotFoundException or NoClassDefFoundError.
 *
 * Flow:
 * 1. Caches plugin availability checks to avoid repeated lookups
 * 2. Provides safe methods to check plugin installation and activation
 * 3. Returns plugin version information when available
 * 4. Enables graceful degradation when plugins are missing
 * 5. Supports multiple plugin types with extensible architecture
 *
 * @author KanbanAI
 * @since 1.0.2
 */
public class PluginAvailabilityService {
    
    private static final Logger LOGGER = Logger.getLogger(PluginAvailabilityService.class.getName());
    
    // Singleton instance
    private static PluginAvailabilityService instance;
    
    // Cache plugin check results to avoid repeated checks
    private Boolean activeChoicesAvailable = null;

    /**
     * Private constructor for Singleton pattern
     */
    private PluginAvailabilityService() {
    }

    /**
     * Gets the unique instance of the service (Singleton pattern)
     * 
     * @return PluginAvailabilityService instance
     */
    public static synchronized PluginAvailabilityService getInstance() {
        if (instance == null) {
            instance = new PluginAvailabilityService();
        }
        return instance;
    }
    
    /**
     * Kiểm tra xem Active Choices Plugin có được cài đặt và kích hoạt hay không
     * 
     * Plugin Active Choices (uno-choice) cung cấp các tính năng dynamic parameters
     * như ChoiceParameter, CascadeChoiceParameter, và DynamicReferenceParameter.
     * 
     * Method này kiểm tra:
     * 1. Plugin có được cài đặt không
     * 2. Plugin có được kích hoạt không
     * 3. Các class chính của plugin có thể load được không
     * 
     * @return true nếu Active Choices plugin khả dụng, false nếu không
     */
    public boolean isActiveChoicesPluginAvailable() {
        // Sử dụng cache để tránh kiểm tra lại nhiều lần
        if (activeChoicesAvailable != null) {
            return activeChoicesAvailable;
        }
        
        try {
            Jenkins jenkins = Jenkins.get();
            if (jenkins == null) {
                LOGGER.log(Level.WARNING, "Jenkins instance không khả dụng");
                activeChoicesAvailable = false;
                return false;
            }
            
            // Kiểm tra plugin có được cài đặt không
            PluginWrapper plugin = jenkins.getPluginManager().getPlugin("uno-choice");
            if (plugin == null) {
                LOGGER.log(Level.INFO, "Active Choices Plugin (uno-choice) chưa được cài đặt");
                activeChoicesAvailable = false;
                return false;
            }
            
            // Kiểm tra plugin có được kích hoạt không
            if (!plugin.isEnabled()) {
                LOGGER.log(Level.INFO, "Active Choices Plugin (uno-choice) đã được cài đặt nhưng chưa được kích hoạt");
                activeChoicesAvailable = false;
                return false;
            }
            
            // Kiểm tra các class chính có thể load được không
            if (!canLoadActiveChoicesClasses()) {
                LOGGER.log(Level.WARNING, "Active Choices Plugin được cài đặt nhưng không thể load các class chính");
                activeChoicesAvailable = false;
                return false;
            }
            
            LOGGER.log(Level.INFO, "Active Choices Plugin (uno-choice) khả dụng - Version: " + plugin.getVersion());
            activeChoicesAvailable = true;
            return true;
            
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Lỗi khi kiểm tra Active Choices Plugin: " + e.getMessage(), e);
            activeChoicesAvailable = false;
            return false;
        }
    }
    
    /**
     * Kiểm tra xem các class chính của Active Choices Plugin có thể load được không
     * 
     * Đây là kiểm tra bổ sung để đảm bảo rằng plugin không chỉ được cài đặt
     * mà còn có thể sử dụng được (không bị conflict dependency, etc.)
     * 
     * @return true nếu có thể load các class chính, false nếu không
     */
    private boolean canLoadActiveChoicesClasses() {
        try {
            // Thử load các class chính của Active Choices Plugin
            Class.forName("org.biouno.unochoice.AbstractScriptableParameter");
            Class.forName("org.biouno.unochoice.ChoiceParameter");
            Class.forName("org.biouno.unochoice.CascadeChoiceParameter");
            Class.forName("org.biouno.unochoice.DynamicReferenceParameter");
            Class.forName("org.biouno.unochoice.model.Script");
            
            return true;
        } catch (ClassNotFoundException e) {
            LOGGER.log(Level.WARNING, "Không thể load Active Choices classes: " + e.getMessage());
            return false;
        } catch (NoClassDefFoundError e) {
            LOGGER.log(Level.WARNING, "NoClassDefFoundError khi load Active Choices classes: " + e.getMessage());
            return false;
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Lỗi không xác định khi load Active Choices classes: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Kiểm tra xem một plugin cụ thể có được cài đặt và kích hoạt hay không
     * 
     * Method generic để kiểm tra bất kỳ plugin nào theo plugin ID
     * 
     * @param pluginId ID của plugin cần kiểm tra (ví dụ: "uno-choice", "workflow-aggregator")
     * @return true nếu plugin khả dụng, false nếu không
     */
    public boolean isPluginAvailable(String pluginId) {
        if (pluginId == null || pluginId.trim().isEmpty()) {
            return false;
        }
        
        try {
            Jenkins jenkins = Jenkins.get();
            if (jenkins == null) {
                return false;
            }
            
            PluginWrapper plugin = jenkins.getPluginManager().getPlugin(pluginId);
            return plugin != null && plugin.isEnabled();
            
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Lỗi khi kiểm tra plugin " + pluginId + ": " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Lấy thông tin version của Active Choices Plugin (nếu có)
     * 
     * @return Version string của plugin, hoặc null nếu plugin không khả dụng
     */
    public String getActiveChoicesPluginVersion() {
        try {
            if (!isActiveChoicesPluginAvailable()) {
                return null;
            }
            
            Jenkins jenkins = Jenkins.get();
            PluginWrapper plugin = jenkins.getPluginManager().getPlugin("uno-choice");
            return plugin != null ? plugin.getVersion() : null;
            
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Lỗi khi lấy version Active Choices Plugin: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Reset cache để force kiểm tra lại plugin availability
     * Hữu ích khi plugin được cài đặt/gỡ bỏ trong runtime
     */
    public void resetCache() {
        activeChoicesAvailable = null;
        LOGGER.log(Level.INFO, "Plugin availability cache đã được reset");
    }
}
