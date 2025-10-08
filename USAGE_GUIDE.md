# AMT-Param Plugin Usage Guide

## Overview

AMT-Param Plugin cung cấp REST API để lấy thông tin đầy đủ về parameters của Jenkins jobs, tương tự như màn hình "Build with Parameters" nhưng qua API.

## Installation

### Prerequisites
- Jenkins 2.414.3 hoặc cao hơn
- Java 11 hoặc cao hơn

### Optional Dependencies
- **Active Choices Plugin (uno-choice) v2.7.2+**: Để hỗ trợ dynamic parameters
  - Nếu không cài đặt: Plugin vẫn hoạt động với basic parameters, Active Choices parameters sẽ fallback thành text input

### Installation Steps
1. Download `amt-param.hpi` file
2. Go to Jenkins → Manage Jenkins → Manage Plugins → Advanced
3. Upload `amt-param.hpi` file
4. Restart Jenkins

## API Endpoint

### Base URL
```
{JENKINS_URL}/amt-param/get
```

### HTTP Method
```
GET
```

### Query Parameters

| Parameter | Required | Description | Example |
|-----------|----------|-------------|---------|
| `job` | Yes | Full URL của Jenkins job | `https://jenkins.example.com/job/myJob/` |
| `params` | No | Current parameter values cho cascade parameters | `env:prod,region:us-east-1` |

## Usage Examples

### 1. Basic Job Parameters
```bash
curl -X GET "https://jenkins.example.com/amt-param/get?job=https://jenkins.example.com/job/myJob/"
```

### 2. Job trong Folder
```bash
curl -X GET "https://jenkins.example.com/amt-param/get?job=https://jenkins.example.com/job/folder1/job/myJob/"
```

### 3. Cascade Parameters (Active Choices)
```bash
curl -X GET "https://jenkins.example.com/amt-param/get?job=https://jenkins.example.com/job/myJob/&params=environment:production,region:us-east-1"
```

### 4. With Authentication
```bash
curl -X GET \
  -u "username:api_token" \
  "https://jenkins.example.com/amt-param/get?job=https://jenkins.example.com/job/myJob/"
```

## Response Format

### Success Response
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
        "description": "Target deployment environment",
        "currentValue": "dev",
        "inputType": "select",
        "isDynamic": true,
        "isRequired": false,
        "choices": ["dev", "staging", "prod"],
        "dependencies": [],
        "errorMessage": null
      },
      {
        "name": "region",
        "type": "CascadeChoiceParameter",
        "description": "AWS region based on environment",
        "currentValue": "us-east-1",
        "inputType": "cascade_select",
        "isDynamic": true,
        "isRequired": false,
        "choices": ["us-east-1", "us-west-2"],
        "dependencies": ["environment"],
        "errorMessage": null
      }
    ]
  }
}
```

### Error Response
```json
{
  "success": false,
  "error": "Job not found",
  "details": "Job 'nonexistent-job' does not exist in Jenkins",
  "statusCode": 404
}
```

## Parameter Types

### Built-in Jenkins Parameters

| Type | Input Type | Description |
|------|------------|-------------|
| `StringParameterDefinition` | `text` | Single line text input |
| `TextParameterDefinition` | `textarea` | Multi-line text input |
| `BooleanParameterDefinition` | `checkbox` | True/false checkbox |
| `ChoiceParameterDefinition` | `select` | Dropdown with fixed choices |
| `PasswordParameterDefinition` | `password` | Password input (value hidden) |

### Active Choices Parameters

| Type | Input Type | Description |
|------|------------|-------------|
| `ChoiceParameter` | `select` | Dynamic dropdown choices |
| `CascadeChoiceParameter` | `cascade_select` | Choices depend on other parameters |
| `DynamicReferenceParameter` | `dynamic_reference` | Dynamic HTML/text content |

## Error Handling

### Common Error Codes

| Status Code | Error | Description |
|-------------|-------|-------------|
| 400 | Bad Request | Missing or invalid parameters |
| 403 | Forbidden | No permission to access job |
| 404 | Not Found | Job does not exist |
| 500 | Internal Server Error | Unexpected server error |

### Graceful Fallback

Plugin được thiết kế để hoạt động ổn định ngay cả khi:
- Active Choices plugin không được cài đặt
- Parameter rendering gặp lỗi
- Script execution thất bại

Trong các trường hợp này, plugin sẽ:
- Fallback về basic parameter types
- Return error message trong parameter object
- Continue processing other parameters

## Integration Examples

### JavaScript/Frontend
```javascript
async function getJobParameters(jobUrl, currentParams = {}) {
  const params = new URLSearchParams({
    job: jobUrl,
    ...(Object.keys(currentParams).length > 0 && {
      params: Object.entries(currentParams)
        .map(([key, value]) => `${key}:${value}`)
        .join(',')
    })
  });
  
  const response = await fetch(`/amt-param/get?${params}`);
  const result = await response.json();
  
  if (!result.success) {
    throw new Error(result.error);
  }
  
  return result.data;
}

// Usage
try {
  const jobParams = await getJobParameters(
    'https://jenkins.example.com/job/myJob/',
    { environment: 'prod' }
  );
  console.log('Job parameters:', jobParams.parameters);
} catch (error) {
  console.error('Error:', error.message);
}
```

### Python
```python
import requests
from urllib.parse import urlencode

def get_job_parameters(jenkins_url, job_url, current_params=None, auth=None):
    """Get job parameters from AMT-Param API"""
    
    params = {'job': job_url}
    if current_params:
        params['params'] = ','.join([f"{k}:{v}" for k, v in current_params.items()])
    
    url = f"{jenkins_url}/amt-param/get"
    response = requests.get(url, params=params, auth=auth)
    
    result = response.json()
    if not result.get('success'):
        raise Exception(f"API Error: {result.get('error')}")
    
    return result['data']

# Usage
try:
    job_params = get_job_parameters(
        jenkins_url='https://jenkins.example.com',
        job_url='https://jenkins.example.com/job/myJob/',
        current_params={'environment': 'prod'},
        auth=('username', 'api_token')
    )
    print(f"Found {len(job_params['parameters'])} parameters")
except Exception as e:
    print(f"Error: {e}")
```

## Troubleshooting

### Common Issues

1. **"Job parameter is required"**
   - Ensure `job` query parameter is provided
   - Check job URL format

2. **"Job not found"**
   - Verify job exists in Jenkins
   - Check job name spelling and folder path

3. **"Access denied"**
   - Ensure user has READ permission for the job
   - Check authentication credentials

4. **Active Choices parameters show as text input**
   - Active Choices plugin may not be installed
   - Check plugin compatibility version

### Debug Information

Enable debug logging by setting log level to FINE for:
- `io.kanbanai.paramsview.service.ParameterRenderingService`
- `io.kanbanai.paramsview.service.ActiveChoicesService`
- `io.kanbanai.paramsview.service.PluginAvailabilityService`

### Plugin Health Check

Check plugin status:
```bash
curl -X GET "https://jenkins.example.com/amt-param/get?job=https://jenkins.example.com/job/test-job/"
```

Look for `activeChoicesPluginAvailable` and `activeChoicesPluginVersion` in response to verify Active Choices integration.
