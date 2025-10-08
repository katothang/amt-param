# 🚀 QUICK START GUIDE

## 1️⃣ Build Plugin (30 giây)

```bash
cd /Users/kuthang/Desktop/code/amt-param
mvn clean package
```

✅ Output: `target/amt-param.hpi`

## 2️⃣ Install vào Jenkins (1 phút)

1. Mở Jenkins UI
2. **Manage Jenkins** → **Manage Plugins** → **Advanced**
3. Upload file `target/amt-param.hpi`
4. Restart Jenkins

## 3️⃣ Test API (10 giây)

```bash
# Test đơn giản
curl "http://localhost:8080/amt-param/get?job=YourJobName"

# Test với cascade parameters
curl "http://localhost:8080/amt-param/get?job=YourJobName&params=env:prod,region:us-east-1"
```

## 📋 API Response

```json
{
  "jobName": "YourJobName",
  "parameters": [
    {
      "name": "environment",
      "type": "ChoiceParameterDefinition",
      "currentValue": "dev",
      "inputType": "select",
      "choices": ["dev", "staging", "prod"],
      "isDynamic": false,
      "dependencies": []
    }
  ]
}
```

## 🎯 Common Use Cases

### Use Case 1: Lấy tất cả parameters
```bash
curl "http://jenkins/amt-param/get?job=MyJob"
```

### Use Case 2: Lấy parameters với current values
```bash
curl "http://jenkins/amt-param/get?job=MyJob&params=env:prod"
```

### Use Case 3: Multi-level cascade
```bash
curl "http://jenkins/amt-param/get?job=MyJob&params=cloud:AWS,region:us-east-1"
```

### Use Case 4: Job trong folder
```bash
curl "http://jenkins/amt-param/get?job=folder/subfolder/MyJob"
```

## ✅ Verification

### 1. Kiểm tra plugin đã install
- Vào **Manage Jenkins** → **Manage Plugins** → **Installed**
- Tìm "amt-param" trong list

### 2. Kiểm tra API hoạt động
```bash
# Nếu response 200 + JSON => OK
curl -v "http://localhost:8080/amt-param/get?job=TestJob"
```

### 3. So sánh với Jenkins UI
1. Mở job → **Build with Parameters**
2. Xem choices trong dropdown
3. Call API và compare
4. Choices phải giống nhau

## 🐛 Troubleshooting

### Lỗi 404 - Job not found
```bash
# Đảm bảo job name đúng, case-sensitive
# Nếu job trong folder, dùng full path: folder/jobname
curl "http://localhost:8080/amt-param/get?job=folder/MyJob"
```

### Lỗi 403 - Access denied
```bash
# User phải có READ permission trên job
# Login hoặc dùng API token
curl -u username:token "http://localhost:8080/amt-param/get?job=MyJob"
```

### Choices rỗng cho Active Choices
```bash
# Kiểm tra Active Choices plugin đã install
# Kiểm tra Groovy script trong parameter definition
# Check Jenkins logs: /var/log/jenkins/jenkins.log
```

### Cascade parameter không đúng
```bash
# Đảm bảo truyền current values đúng format
# Format: param1:value1,param2:value2
curl "http://localhost:8080/amt-param/get?job=MyJob&params=env:prod,region:us-east-1"
```

## 📖 Đọc thêm

- **Chi tiết thay đổi:** [CHANGELOG.md](CHANGELOG.md)
- **Cách hoạt động:** [HOW_IT_WORKS.md](HOW_IT_WORKS.md)
- **Test cases:** [TEST_CASES.md](TEST_CASES.md)
- **Tóm tắt:** [SUMMARY.md](SUMMARY.md)

## 💡 Tips

### Tip 1: Pretty print JSON
```bash
curl "http://localhost:8080/amt-param/get?job=MyJob" | jq .
```

### Tip 2: Save to file
```bash
curl "http://localhost:8080/amt-param/get?job=MyJob" > params.json
```

### Tip 3: Test với authentication
```bash
# Dùng username:password
curl -u admin:password "http://localhost:8080/amt-param/get?job=MyJob"

# Dùng API token (recommended)
curl -u admin:1234567890abcdef "http://localhost:8080/amt-param/get?job=MyJob"
```

### Tip 4: Debug mode
```bash
# Enable verbose output
curl -v "http://localhost:8080/amt-param/get?job=MyJob"
```

## 🎓 Ví dụ Integration

### Python
```python
import requests

response = requests.get(
    'http://localhost:8080/amt-param/get',
    params={'job': 'MyJob', 'params': 'env:prod'},
    auth=('admin', 'token')
)

data = response.json()
for param in data['parameters']:
    print(f"{param['name']}: {param['choices']}")
```

### JavaScript
```javascript
fetch('http://localhost:8080/amt-param/get?job=MyJob')
  .then(response => response.json())
  .then(data => {
    data.parameters.forEach(param => {
      console.log(`${param.name}: ${param.choices}`);
    });
  });
```

### Bash Script
```bash
#!/bin/bash

JOB_NAME="MyJob"
JENKINS_URL="http://localhost:8080"
API_URL="${JENKINS_URL}/amt-param/get?job=${JOB_NAME}"

# Get parameters
RESPONSE=$(curl -s "$API_URL")

# Parse JSON (requires jq)
echo "$RESPONSE" | jq '.parameters[] | {name, choices}'
```

## ⚡ Performance

| Scenario | Response Time |
|----------|---------------|
| 1 parameter | < 100ms |
| 10 parameters | < 500ms |
| 50 parameters | < 2s |
| Cascade (2 levels) | < 300ms |
| Active Choices với API call | < 1s |

## 🔒 Security

- ✅ Respect Jenkins permissions (READ permission required)
- ✅ No script injection (dùng Jenkins API)
- ✅ Password parameters return empty string
- ✅ Same security context as Jenkins UI

---

**Có câu hỏi?** Đọc [HOW_IT_WORKS.md](HOW_IT_WORKS.md) để hiểu chi tiết!
