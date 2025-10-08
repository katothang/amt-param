# AMT-Param Plugin Architecture Documentation

## Overview

AMT-Param Plugin đã được refactor từ monolithic architecture sang layered architecture với separation of concerns rõ ràng. Version 1.0.2+ sử dụng service layer pattern và type-safe models.

## Architecture Layers

### 1. Controller Layer
- **File**: `RenderedParametersApi.java`
- **Responsibility**: HTTP request/response handling, input validation, error handling
- **Dependencies**: ParameterRenderingService, PluginAvailabilityService

### 2. Service Layer
- **ParameterRenderingService**: Core business logic cho parameter rendering
- **ActiveChoicesService**: Specialized service cho Active Choices Plugin integration
- **PluginAvailabilityService**: Plugin availability checking và graceful fallback

### 3. Model Layer
- **RenderedParametersInfo**: Type-safe model cho API response
- **RenderedParameterInfo**: Type-safe model cho individual parameter
- **ParameterInputType**: Enum cho UI input types

## Key Improvements

### 1. Plugin Availability Checking
```java
// Before: Direct usage (causes ClassNotFoundException if plugin not installed)
if (def instanceof AbstractScriptableParameter) { ... }

// After: Safe checking with graceful fallback
if (pluginService.isActiveChoicesPluginAvailable()) {
    // Use Active Choices features
} else {
    // Fallback to basic functionality
}
```

### 2. Type Safety
```java
// Before: Generic Object usage
Object scriptResult = script.eval(currentValues);
List<String> choices = (List<String>) scriptResult; // Unsafe cast

// After: Type-safe models
RenderedParameterInfo paramInfo = new RenderedParameterInfo();
paramInfo.setInputType(ParameterInputType.SELECT);
paramInfo.setChoices(normalizedChoices);
```

### 3. Separation of Concerns
```java
// Before: Everything in one class (900+ lines)
public class RenderedParametersApi {
    // HTTP handling + business logic + Active Choices + JSON serialization
}

// After: Clean separation
public class RenderedParametersApi {
    // Only HTTP handling
    private final ParameterRenderingService parameterService;
}

public class ParameterRenderingService {
    // Only parameter rendering logic
}

public class ActiveChoicesService {
    // Only Active Choices integration
}
```

## Service Dependencies

### Singleton Pattern
All services sử dụng Singleton pattern để lightweight dependency injection:

```java
public class ParameterRenderingService {
    private static ParameterRenderingService instance;
    
    public static synchronized ParameterRenderingService getInstance() {
        if (instance == null) {
            instance = new ParameterRenderingService();
        }
        return instance;
    }
}
```

### Dependency Graph
```
RenderedParametersApi
├── ParameterRenderingService
│   ├── PluginAvailabilityService
│   └── ActiveChoicesService
│       └── PluginAvailabilityService
└── PluginAvailabilityService
```

## Error Handling Strategy

### 1. Graceful Degradation
- Plugin không khả dụng → Fallback to basic functionality
- Parameter render lỗi → Return error parameter instead of crash
- Script execution lỗi → Return empty choices

### 2. Consistent Error Response Format
```json
{
  "success": false,
  "error": "Error message",
  "details": "Detailed description",
  "statusCode": 400
}
```

### 3. Logging Strategy
- INFO: Normal operations, plugin availability
- WARNING: Non-critical errors, fallbacks
- SEVERE: Critical errors that affect functionality

## Active Choices Integration

### Safe Reflection Usage
```java
// Check plugin availability first
if (!pluginService.isActiveChoicesPluginAvailable()) {
    return fallbackBehavior();
}

// Use reflection safely
try {
    Object script = invokeMethod(paramDef, "getScript");
    Object result = invokeMethod(script, "eval", Map.class, currentValues);
    return normalizeChoicesResult(result);
} catch (Exception e) {
    // Log and fallback
    return fallbackChoices();
}
```

### Supported Active Choices Types
1. **ChoiceParameter**: Static dropdown choices
2. **CascadeChoiceParameter**: Dynamic choices based on other parameters
3. **DynamicReferenceParameter**: Dynamic HTML/text content

## API Usage

### Basic Usage
```bash
GET /amt-param/get?job=https://jenkins.example.com/job/myJob/
```

### With Cascade Parameters
```bash
GET /amt-param/get?job=https://jenkins.example.com/job/myJob/&params=env:prod,region:us-east-1
```

### Response Format
```json
{
  "success": true,
  "data": {
    "jobName": "myJob",
    "jobFullName": "folder/myJob",
    "jobUrl": "job/folder/job/myJob/",
    "buildWithParametersUrl": "job/folder/job/myJob/buildWithParameters",
    "activeChoicesPluginAvailable": true,
    "activeChoicesPluginVersion": "2.7.2",
    "parameters": [
      {
        "name": "environment",
        "type": "ChoiceParameter",
        "description": "Target environment",
        "currentValue": "dev",
        "inputType": "select",
        "isDynamic": true,
        "isRequired": false,
        "choices": ["dev", "staging", "prod"],
        "dependencies": [],
        "errorMessage": null
      }
    ]
  }
}
```

## Testing Strategy

### Unit Tests Coverage
1. **PluginAvailabilityService**: Plugin detection logic
2. **ParameterRenderingService**: Parameter rendering for all types
3. **ActiveChoicesService**: Reflection-based Active Choices integration
4. **Model Classes**: JSON serialization/deserialization

### Integration Tests
1. **With Active Choices Plugin**: Full functionality
2. **Without Active Choices Plugin**: Graceful fallback
3. **Error Scenarios**: Invalid jobs, permission denied, etc.

## Performance Considerations

### Caching
- Plugin availability results are cached
- Avoid repeated reflection calls
- Lazy initialization of services

### Memory Usage
- Singleton services reduce memory footprint
- Efficient JSON serialization without external libraries
- Proper resource cleanup

## Migration Guide

### From v1.0.1 to v1.0.2+

1. **API Response Format**: Wrapped in success/data structure
2. **Error Handling**: Consistent error response format
3. **Plugin Dependencies**: Graceful handling when Active Choices not available
4. **Type Safety**: Strongly typed models instead of generic Objects

### Backward Compatibility
- API endpoint remains the same: `/amt-param/get`
- Query parameters unchanged
- Core functionality preserved
- Enhanced error handling and reliability
