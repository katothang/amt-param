# AMT Parameters Plugin - API Usage Guide

## Tổng quan

Plugin cung cấp 2 cách để lấy thông tin parameters của Jenkins job:

### 1. Job API Endpoint (Khuyến nghị) - v1.0.3+

Truy cập API endpoint của job với query parameter `params`:

```
{JENKINS_URL}/job/{JOB_NAME}/api?params=param1:value1,param2:value2
```

**Ưu điểm:**
- URL ngắn gọn, dễ nhớ
- Tích hợp trực tiếp vào Jenkins job
- Không cần biết full Jenkins URL trong query string
- Follow REST API convention (`/api` endpoint)

**Ví dụ:**
```bash
# Lấy parameters của job AMT_param với Channel=C01
curl "https://jenkins.thangnotes.dev/job/AMT_param/api?params=Channel:C01"

# Lấy parameters với nhiều giá trị
curl "https://jenkins.thangnotes.dev/job/AMT_param/api?params=Channel:C01,Environment:prod"

# Lấy tất cả parameters (không có current values)
curl "https://jenkins.thangnotes.dev/job/AMT_param/api"

# Lấy parameters của job trong folder
curl "https://jenkins.thangnotes.dev/job/MyFolder/job/MyJob/api?params=param1:value1"
```

### 2. Legacy REST API (Vẫn được hỗ trợ)

Sử dụng REST API endpoint với full job URL:

```
{JENKINS_URL}/amt-integration/get?job={FULL_JOB_URL}&params=param1:value1,param2:value2
```

**Ví dụ:**
```bash
# Cách cũ - vẫn hoạt động nhưng không khuyến nghị
curl "https://jenkins.thangnotes.dev/amt-integration/get?job=https://jenkins.thangnotes.dev/job/AMT_param&params=Channel:C01"
```

## Response Format

Cả 2 cách đều trả về cùng một JSON response format:

### Success Response (200 OK)

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
      },
      {
        "name": "Environment",
        "type": "STRING",
        "inputType": "text",
        "description": "Target environment",
        "defaultValue": "dev",
        "required": false
      }
    ],
    "totalParameters": 2
  }
}
```

### Error Response (4xx/5xx)

```json
{
  "success": false,
  "error": "Access denied to job: AMT_param",
  "details": "You don't have READ permission for this job",
  "statusCode": 403
}
```

## Parameter Format

Query parameter `params` sử dụng format:

```
param1:value1,param2:value2,param3:value3
```

**Lưu ý:**
- Các parameters được phân tách bởi dấu `,` (comma)
- Tên parameter và giá trị được phân tách bởi dấu `:` (colon)
- Values có thể chứa spaces: `param1:value with spaces`
- Nếu value chứa comma hoặc colon đặc biệt, cần URL encode

**Ví dụ URL Encoding:**
```bash
# Value có chứa comma: "a,b,c"
curl "https://jenkins.thangnotes.dev/job/MyJob?params=param1:a%2Cb%2Cc"

# Value có chứa colon: "http://example.com"
curl "https://jenkins.thangnotes.dev/job/MyJob?params=url:http%3A%2F%2Fexample.com"
```

## Authentication

Plugin sử dụng Jenkins authentication và authorization:

- API sẽ kiểm tra quyền READ trên job
- Sử dụng Jenkins credentials (session cookie, API token, etc.)
- Nếu không có quyền, trả về HTTP 403

**Ví dụ với API Token:**
```bash
curl -u username:api_token \
  "https://jenkins.thangnotes.dev/job/AMT_param?params=Channel:C01"
```

## Use Cases

### 1. Dynamic Form Generation

Lấy thông tin parameters để tạo dynamic form trong web app:

```javascript
async function getJobParameters(jobUrl, currentValues = {}) {
  // Convert currentValues object to params string
  const paramsStr = Object.entries(currentValues)
    .map(([key, value]) => `${key}:${value}`)
    .join(',');
  
  const url = `${jobUrl}/api?params=${encodeURIComponent(paramsStr)}`;
  const response = await fetch(url);
  const data = await response.json();
  
  if (data.success) {
    return data.data.parameters;
  } else {
    throw new Error(data.error);
  }
}

// Usage
const params = await getJobParameters(
  'https://jenkins.thangnotes.dev/job/AMT_param',
  { Channel: 'C01' }
);

// Render form based on params...
```

### 2. Cascade Parameters

Lấy parameters phụ thuộc khi giá trị parameter cha thay đổi:

```javascript
async function onChannelChange(channel) {
  // Get updated parameters based on selected channel
  const params = await getJobParameters(
    'https://jenkins.thangnotes.dev/job/AMT_param',
    { Channel: channel }
  );
  
  // Update dependent dropdowns
  updateEnvironmentDropdown(params.find(p => p.name === 'Environment'));
}
```

### 3. API Integration

Tích hợp với external systems:

```python
import requests

def get_jenkins_job_params(job_url, params=None):
    """Get Jenkins job parameters"""
    url = f"{job_url}/api"
    if params:
        params_str = ','.join([f"{k}:{v}" for k, v in params.items()])
        url = f"{job_url}/api?params={params_str}"
    
    response = requests.get(url, auth=('username', 'api_token'))
    response.raise_for_status()
    
    data = response.json()
    if data['success']:
        return data['data']['parameters']
    else:
        raise Exception(data['error'])

# Usage
params = get_jenkins_job_params(
    'https://jenkins.thangnotes.dev/job/AMT_param',
    {'Channel': 'C01'}
)
```

## Supported Parameter Types

Plugin hỗ trợ tất cả loại parameters của Jenkins:

### Built-in Parameters
- **STRING**: Text input
- **TEXT**: Textarea
- **BOOLEAN**: Checkbox
- **CHOICE**: Dropdown select
- **PASSWORD**: Password input
- **FILE**: File upload
- **RUN**: Build selector

### Active Choices Plugin (nếu có)
- **ACTIVE_CHOICE**: Dynamic dropdown
- **ACTIVE_CHOICE_REACTIVE**: Cascade dropdown
- **ACTIVE_CHOICE_PARAMETER**: Complex dynamic parameter

## Migration Guide

### Từ Legacy API sang Job API Endpoint mới

**Before (Legacy API):**
```javascript
const apiUrl = 'https://jenkins.thangnotes.dev/amt-integration/get';
const jobUrl = 'https://jenkins.thangnotes.dev/job/AMT_param';
const params = 'Channel:C01,Environment:prod';

const url = `${apiUrl}?job=${encodeURIComponent(jobUrl)}&params=${encodeURIComponent(params)}`;
```

**After (New Job API Endpoint):**
```javascript
const jobUrl = 'https://jenkins.thangnotes.dev/job/AMT_param';
const params = 'Channel:C01,Environment:prod';

const url = `${jobUrl}/api?params=${encodeURIComponent(params)}`;
```

**Lợi ích:**
- Giảm 40% độ dài URL
- Không cần encode job URL trong query string  
- Dễ đọc và maintain hơn
- Follow REST API best practices

## Troubleshooting

### Lỗi 404 - Not Found

**Nguyên nhân:**
- Job không tồn tại
- Job URL không đúng format
- Thiếu `/api` endpoint

**Giải pháp:**
- Kiểm tra job name và folder path
- Đảm bảo URL có format: `/job/{name}/job/{name}/.../api`
- Thêm `/api` vào cuối job URL

### Lỗi 403 - Access Denied

**Nguyên nhân:**
- Không có quyền READ trên job
- Chưa authenticate

**Giải pháp:**
- Đăng nhập Jenkins
- Sử dụng API token
- Kiểm tra job permissions

### Lỗi 500 - Internal Server Error

**Nguyên nhân:**
- Lỗi trong parameter rendering
- Active Choices script error
- Plugin conflict

**Giải pháp:**
- Kiểm tra Jenkins logs
- Test parameters trong Jenkins UI
- Liên hệ admin

## Performance Notes

- Plugin cache parameter definitions
- Active Choices được evaluate realtime
- Cascade parameters có thể tốn thời gian với scripts phức tạp
- Recommend: Implement client-side caching cho static parameters

## Security Considerations

- Plugin tuân thủ Jenkins security model
- Không bypass permission checks
- Không expose sensitive parameter values (passwords)
- Log all API access for auditing

## Version History

### v1.0.3 (October 2025)
- ✅ Thêm Job API endpoint: `/job/{JOB_NAME}/api?params=...`
- ✅ Giữ backward compatibility với legacy API
- ✅ Update documentation
- ✅ Theo REST API best practices

### v1.0.2
- Initial release với legacy API endpoint
- Support Active Choices plugin
- Service layer architecture

## Support

- GitHub Issues: [Repository URL]
- Documentation: [Docs URL]
- Email: [Support Email]
