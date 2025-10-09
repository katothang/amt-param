# AMT Parameters Plugin - Version 1.0.3 Changes

## Tóm tắt

Version 1.0.3 giới thiệu **Job API Endpoint** mới, cung cấp cách đơn giản hơn để truy cập thông tin parameters của Jenkins job.

## URL Pattern Mới

### Before (v1.0.2 - Legacy API)
```
https://jenkins.thangnotes.dev/amt-integration/get?job=https://jenkins.thangnotes.dev/job/AMT_param&params=Channel:C01
```

### After (v1.0.3 - Job API Endpoint)
```
https://jenkins.thangnotes.dev/job/AMT_param/api?params=Channel:C01
```

## Lợi ích

1. **URL ngắn gọn hơn 40%**: Không cần truyền full job URL trong query parameter
2. **Dễ nhớ và sử dụng**: Follow REST API convention với `/api` endpoint
3. **Tích hợp tốt hơn**: API endpoint nằm trực tiếp trong job URL
4. **Backward Compatible**: Legacy API vẫn hoạt động bình thường

## Kiến trúc Implementation

### Các files mới

1. **`RenderedParametersAction.java`**
   - TransientActionFactory để inject action vào tất cả Job objects
   - Tạo endpoint `/api` cho mỗi job
   - Xử lý query parameter `params` và trả về JSON response
   - Reuse logic từ ParameterRenderingService

### Các files cập nhật

1. **`RenderedParametersApi.java`**
   - Giữ nguyên legacy API endpoint
   - Thêm deprecation notes trong javadoc
   - Update documentation links

### Workflow

```
Request: GET /job/AMT_param/api?params=Channel:C01
    ↓
RenderedParametersAction.doIndex()
    ↓
Check permissions (Item.READ)
    ↓
Parse params query string
    ↓
ParameterRenderingService.renderJobParameters()
    ↓
Return JSON response
```

## Usage Examples

### JavaScript
```javascript
async function getJobParameters(jobUrl, currentValues = {}) {
  const paramsStr = Object.entries(currentValues)
    .map(([key, value]) => `${key}:${value}`)
    .join(',');
  
  const url = `${jobUrl}/api?params=${encodeURIComponent(paramsStr)}`;
  const response = await fetch(url);
  return await response.json();
}

// Usage
const result = await getJobParameters(
  'https://jenkins.thangnotes.dev/job/AMT_param',
  { Channel: 'C01' }
);
console.log(result.data.parameters);
```

### Python
```python
import requests

def get_jenkins_job_params(job_url, params=None):
    url = f"{job_url}/api"
    if params:
        params_str = ','.join([f"{k}:{v}" for k, v in params.items()])
        url = f"{job_url}/api?params={params_str}"
    
    response = requests.get(url, auth=('username', 'api_token'))
    return response.json()

# Usage
result = get_jenkins_job_params(
    'https://jenkins.thangnotes.dev/job/AMT_param',
    {'Channel': 'C01'}
)
print(result['data']['parameters'])
```

### cURL
```bash
# Get parameters với current values
curl "https://jenkins.thangnotes.dev/job/AMT_param/api?params=Channel:C01"

# Get tất cả parameters (no current values)
curl "https://jenkins.thangnotes.dev/job/AMT_param/api"

# With authentication
curl -u username:api_token \
  "https://jenkins.thangnotes.dev/job/AMT_param/api?params=Channel:C01"
```

## Response Format

Cả 2 API (mới và legacy) đều trả về cùng format:

### Success (200)
```json
{
  "success": true,
  "data": {
    "jobName": "AMT_param",
    "jobUrl": "https://jenkins.thangnotes.dev/job/AMT_param/",
    "parameters": [
      {
        "name": "Channel",
        "type": "CHOICE",
        "inputType": "select",
        "description": "Select channel",
        "defaultValue": "C01",
        "required": true,
        "choices": ["C01", "C02", "C03"]
      }
    ],
    "totalParameters": 1
  }
}
```

### Error (4xx/5xx)
```json
{
  "success": false,
  "error": "Access denied to job: AMT_param",
  "details": "You don't have READ permission for this job",
  "statusCode": 403
}
```

## Migration Guide

### Step 1: Update API URLs

Find và replace trong codebase:

```javascript
// Old pattern
const apiUrl = 'https://jenkins.thangnotes.dev/amt-integration/get';
const url = `${apiUrl}?job=${encodeURIComponent(jobUrl)}&params=${params}`;

// New pattern
const url = `${jobUrl}/api?params=${params}`;
```

### Step 2: Test

Test với job thực tế:

```bash
# Test new API
curl "https://jenkins.thangnotes.dev/job/AMT_param/api?params=Channel:C01"

# Compare with legacy API
curl "https://jenkins.thangnotes.dev/amt-integration/get?job=https://jenkins.thangnotes.dev/job/AMT_param&params=Channel:C01"

# Both should return identical results
```

### Step 3: Deploy

1. Build plugin: `mvn clean compile hpi:hpi -DskipTests`
2. Upload `target/amt-param.hpi` to Jenkins
3. Restart Jenkins
4. Verify cả 2 APIs đều hoạt động

## Technical Details

### Plugin Structure

```
amt-param/
├── src/main/java/io/kanbanai/paramsview/
│   ├── RenderedParametersApi.java          # Legacy API endpoint
│   ├── RenderedParametersAction.java       # NEW: Job API action
│   ├── model/
│   │   ├── RenderedParametersInfo.java
│   │   ├── RenderedParameterInfo.java
│   │   └── ParameterInputType.java
│   └── service/
│       ├── ParameterRenderingService.java
│       ├── ActiveChoicesService.java
│       └── PluginAvailabilityService.java
└── target/
    └── amt-param.hpi                        # Deployable plugin
```

### Key Classes

#### RenderedParametersAction
- Implements `hudson.model.Action`
- Factory class extends `TransientActionFactory<Job>`
- Automatically injected into all parameterized jobs
- Provides `/api` endpoint for each job
- Reuses ParameterRenderingService for business logic

#### TransientActionFactory Pattern
```java
@Extension
public static class Factory extends TransientActionFactory<Job> {
    @Override
    public Class<Job> type() {
        return Job.class;
    }

    @Nonnull
    @Override
    public Collection<? extends Action> createFor(@Nonnull Job target) {
        if (target.getProperty(ParametersDefinitionProperty.class) != null) {
            return Collections.singleton(new RenderedParametersAction(target));
        }
        return Collections.emptyList();
    }
}
```

## Testing

### Unit Tests (TODO)
- Test parameter parsing
- Test permission checking
- Test error handling
- Test JSON serialization

### Integration Tests
```bash
# Test with real Jenkins instance
mvn hpi:run

# Access test URLs:
# http://localhost:8080/jenkins/job/TestJob/api?params=param1:value1
# http://localhost:8080/jenkins/amt-integration/get?job=...
```

## Compatibility

- **Jenkins Version**: 2.414.3+
- **Java Version**: 11+
- **Active Choices Plugin**: Optional (graceful degradation if not available)
- **Backward Compatibility**: 100% - legacy API vẫn hoạt động

## Performance

- **Overhead**: Minimal - action được cached bởi Jenkins
- **Response Time**: Tương tự legacy API (~50-200ms cho simple jobs)
- **Memory**: Negligible - action objects are lightweight

## Security

- Tuân thủ Jenkins security model
- Kiểm tra `Item.READ` permission
- Không bypass authentication/authorization
- Log tất cả API access

## Known Limitations

1. **Stapler URL Binding**: Action phải có URL name khác null để có endpoint riêng
2. **Query Parameter Required**: Không thể intercept job root URL trực tiếp
3. **Plugin Dependency**: Yêu cầu ParametersDefinitionProperty trên job

## Future Improvements

1. **Shorter URL**: Research cách để support `/job/{NAME}?params=...` directly
2. **WebSocket Support**: Real-time parameter updates
3. **GraphQL API**: Flexible query capabilities
4. **OpenAPI Spec**: Generate API documentation automatically

## Documentation

- **API Usage Guide**: `API_USAGE.md`
- **Architecture**: `ARCHITECTURE.md`
- **Changelog**: `CHANGELOG.md`

## Credits

- Plugin Author: KanbanAI
- Version: 1.0.3
- Release Date: October 8, 2025
