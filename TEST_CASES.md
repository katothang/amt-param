# TEST CASES - Kiểm tra Plugin Parameters

## 🧪 Các trường hợp cần test

### 1. Built-in Parameters

#### Test 1.1: String Parameter
**Setup trong Jenkins:**
```
Name: user_name
Type: String Parameter
Default: "admin"
Description: "Enter username"
```

**Test API:**
```bash
curl "http://localhost:8080/amt-param/get?job=TestJob"
```

**Expected Result:**
```json
{
  "parameters": [{
    "name": "user_name",
    "type": "StringParameterDefinition",
    "description": "Enter username",
    "currentValue": "admin",
    "inputType": "text",
    "isDynamic": false,
    "dependencies": [],
    "choices": []
  }]
}
```

#### Test 1.2: Boolean Parameter
**Setup trong Jenkins:**
```
Name: enable_feature
Type: Boolean Parameter
Default: true
Description: "Enable new feature"
```

**Expected Result:**
```json
{
  "name": "enable_feature",
  "type": "BooleanParameterDefinition",
  "currentValue": "true",
  "inputType": "checkbox",
  "choices": ["true", "false"]
}
```

#### Test 1.3: Choice Parameter
**Setup trong Jenkins:**
```
Name: environment
Type: Choice Parameter
Choices: dev, staging, prod
Default: dev
```

**Expected Result:**
```json
{
  "name": "environment",
  "type": "ChoiceParameterDefinition",
  "currentValue": "dev",
  "inputType": "select",
  "choices": ["dev", "staging", "prod"]
}
```

### 2. Active Choices Parameters

#### Test 2.1: Simple Active Choices
**Setup trong Jenkins:**
```groovy
Name: dynamic_env
Type: Active Choices Parameter
Groovy Script:
  return ["development", "staging", "production"]
```

**Test API:**
```bash
curl "http://localhost:8080/amt-param/get?job=TestJob"
```

**Expected Result:**
```json
{
  "name": "dynamic_env",
  "type": "ChoiceParameter",
  "inputType": "select",
  "isDynamic": true,
  "choices": ["development", "staging", "production"]
}
```

#### Test 2.2: Active Choices với Conditional
**Setup trong Jenkins:**
```groovy
Name: tier
Type: Active Choices Parameter
Groovy Script:
  def date = new Date()
  def hour = date.hours
  
  if (hour < 12) {
    return ["morning-tier1", "morning-tier2"]
  } else if (hour < 18) {
    return ["afternoon-tier1", "afternoon-tier2"]
  } else {
    return ["evening-tier1", "evening-tier2"]
  }
```

**Test API:**
```bash
# Gọi vào các thời điểm khác nhau trong ngày
curl "http://localhost:8080/amt-param/get?job=TestJob"
```

**Expected Result:**
- Sáng: `["morning-tier1", "morning-tier2"]`
- Chiều: `["afternoon-tier1", "afternoon-tier2"]`
- Tối: `["evening-tier1", "evening-tier2"]`

#### Test 2.3: Cascade Choice Parameter
**Setup trong Jenkins:**

**Parameter 1: environment**
```groovy
Name: environment
Type: Active Choices Parameter
Script:
  return ["dev", "staging", "prod"]
```

**Parameter 2: region (cascade)**
```groovy
Name: region
Type: Active Choices Reactive Parameter
Referenced parameters: environment
Groovy Script:
  def envRegions = [
    "dev": ["dev-us-1", "dev-eu-1"],
    "staging": ["stg-us-1", "stg-eu-1", "stg-ap-1"],
    "prod": ["prod-us-east-1", "prod-us-west-2", "prod-eu-west-1", "prod-ap-southeast-1"]
  ]
  
  return envRegions[environment] ?: ["default"]
```

**Test API - Dev Environment:**
```bash
curl "http://localhost:8080/amt-param/get?job=TestJob&params=environment:dev"
```

**Expected Result:**
```json
{
  "parameters": [
    {
      "name": "environment",
      "choices": ["dev", "staging", "prod"],
      "currentValue": "dev"
    },
    {
      "name": "region",
      "dependencies": ["environment"],
      "choices": ["dev-us-1", "dev-eu-1"],
      "isDynamic": true
    }
  ]
}
```

**Test API - Prod Environment:**
```bash
curl "http://localhost:8080/amt-param/get?job=TestJob&params=environment:prod"
```

**Expected Result:**
```json
{
  "parameters": [
    {
      "name": "environment",
      "choices": ["dev", "staging", "prod"],
      "currentValue": "prod"
    },
    {
      "name": "region",
      "dependencies": ["environment"],
      "choices": ["prod-us-east-1", "prod-us-west-2", "prod-eu-west-1", "prod-ap-southeast-1"],
      "isDynamic": true
    }
  ]
}
```

#### Test 2.4: Multi-level Cascade
**Setup:**

**Param 1: cloud_provider**
```groovy
return ["AWS", "Azure", "GCP"]
```

**Param 2: region (depends on cloud_provider)**
```groovy
def regions = [
  "AWS": ["us-east-1", "us-west-2", "eu-west-1"],
  "Azure": ["eastus", "westeurope", "southeastasia"],
  "GCP": ["us-central1", "europe-west1", "asia-east1"]
]
return regions[cloud_provider]
```

**Param 3: instance_type (depends on cloud_provider and region)**
```groovy
if (cloud_provider == "AWS") {
  if (region.startsWith("us")) {
    return ["t3.micro", "t3.small", "t3.medium"]
  } else {
    return ["t3.small", "t3.medium", "t3.large"]
  }
} else if (cloud_provider == "Azure") {
  return ["Standard_B1s", "Standard_B2s", "Standard_D2s_v3"]
} else {
  return ["n1-standard-1", "n1-standard-2", "n1-standard-4"]
}
```

**Test:**
```bash
curl "http://localhost:8080/amt-param/get?job=TestJob&params=cloud_provider:AWS,region:us-east-1"
```

**Expected:**
```json
{
  "parameters": [
    {"name": "cloud_provider", "choices": ["AWS", "Azure", "GCP"]},
    {"name": "region", "dependencies": ["cloud_provider"], "choices": ["us-east-1", "us-west-2", "eu-west-1"]},
    {"name": "instance_type", "dependencies": ["cloud_provider", "region"], "choices": ["t3.micro", "t3.small", "t3.medium"]}
  ]
}
```

### 3. Dynamic Reference Parameter

#### Test 3.1: Display Dynamic HTML
**Setup:**
```groovy
Name: server_info
Type: Dynamic Reference Parameter
Referenced parameters: environment, region
Script:
  def info = """
    <div style='padding: 10px; background: #f0f0f0;'>
      <h3>Selected Configuration</h3>
      <p><strong>Environment:</strong> ${environment}</p>
      <p><strong>Region:</strong> ${region}</p>
      <p><strong>Server:</strong> ${environment}-${region}-server-01</p>
    </div>
  """
  return info
```

**Test:**
```bash
curl "http://localhost:8080/amt-param/get?job=TestJob&params=environment:prod,region:us-east-1"
```

**Expected:**
HTML content hiển thị thông tin đã chọn

### 4. Complex Scenarios

#### Test 4.1: API Call trong Script
**Setup:**
```groovy
Name: available_versions
Type: Active Choices Parameter
Groovy Script:
  import groovy.json.JsonSlurper
  
  def url = "https://api.github.com/repos/jenkins-ci/jenkins/releases"
  def json = new JsonSlurper().parse(new URL(url))
  def versions = json.take(5).collect { it.tag_name }
  
  return versions
```

**Test:**
```bash
curl "http://localhost:8080/amt-param/get?job=TestJob"
```

**Expected:**
List 5 Jenkins releases mới nhất từ GitHub

#### Test 4.2: Script với Error Handling
**Setup:**
```groovy
Name: safe_options
Type: Active Choices Parameter
Script:
  try {
    // Thử lấy từ API
    def url = "https://api.example.com/options"
    def json = new JsonSlurper().parse(new URL(url))
    return json.options
  } catch (Exception e) {
    // Fallback nếu API fail
    return ["option1", "option2", "option3"]
  }
```

**Test:**
API fail → Should return fallback options

#### Test 4.3: Script sử dụng Jenkins Environment
**Setup:**
```groovy
Name: build_numbers
Type: Active Choices Parameter
Referenced parameters: job_name
Script:
  import jenkins.model.Jenkins
  
  def jenkins = Jenkins.instance
  def job = jenkins.getItem(job_name)
  
  if (job) {
    def builds = job.builds.take(10).collect { "#${it.number}" }
    return builds
  }
  
  return ["No builds found"]
```

**Test:**
Should return last 10 build numbers of selected job

## ✅ Verification Checklist

### Functional Tests
- [ ] Built-in parameters (String, Boolean, Choice, Text, Password)
- [ ] Simple Active Choices
- [ ] Active Choices with conditional logic
- [ ] Single-level cascade
- [ ] Multi-level cascade
- [ ] Dynamic Reference Parameter
- [ ] Scripts with API calls
- [ ] Scripts with error handling
- [ ] Scripts using Jenkins API

### Edge Cases
- [ ] Empty parameter list
- [ ] Parameter with empty choices
- [ ] Null default values
- [ ] Special characters in parameter names
- [ ] Very long choice lists (>1000 items)
- [ ] Circular dependencies between parameters
- [ ] Non-existent job
- [ ] Job without parameters
- [ ] User without READ permission

### Performance Tests
- [ ] Job with 1 parameter - response time < 100ms
- [ ] Job with 10 parameters - response time < 500ms
- [ ] Job with 50 parameters - response time < 2s
- [ ] Cascade parameter with 1000 choices - response time < 1s

### Compatibility Tests
- [ ] Active Choices Plugin v2.0
- [ ] Active Choices Plugin v2.1
- [ ] Active Choices Plugin v2.2+
- [ ] Jenkins LTS 2.387.x
- [ ] Jenkins LTS 2.401.x
- [ ] Jenkins LTS 2.414.x

## 🐛 Known Issues & Workarounds

### Issue 1: Plugin version compatibility
**Problem:** Different Active Choices versions use different method names
**Solution:** Plugin tries multiple method signatures (getChoicesForUI, getValues, getChoices)

### Issue 2: Large choice lists performance
**Problem:** Jobs with >10000 choices may be slow
**Solution:** Consider pagination or filtering at API level

### Issue 3: Security with script execution
**Problem:** Malicious scripts could access Jenkins internals
**Solution:** Plugin uses same security context as Jenkins UI

## 📊 Test Results Template

```
Test Date: ___________
Jenkins Version: ___________
Active Choices Version: ___________

| Test ID | Description | Status | Notes |
|---------|-------------|--------|-------|
| 1.1 | String Parameter | ✅ | |
| 1.2 | Boolean Parameter | ✅ | |
| 1.3 | Choice Parameter | ✅ | |
| 2.1 | Simple Active Choices | ✅ | |
| 2.2 | Conditional Active Choices | ✅ | |
| 2.3 | Cascade 2-level | ✅ | |
| 2.4 | Cascade 3-level | ✅ | |
| 3.1 | Dynamic Reference | ✅ | |
| 4.1 | API Call Script | ✅ | |
| 4.2 | Error Handling | ✅ | |
| 4.3 | Jenkins API Usage | ✅ | |
```

## 🔧 Debug Tips

### Enable debug logging
```bash
# Add to Jenkins system properties
-Djava.util.logging.config.file=logging.properties

# logging.properties
io.kanbanai.paramsview.level=FINE
```

### Test individual methods
```bash
# Test parameter parsing
curl "http://localhost:8080/amt-param/get?job=TestJob&params=a:1,b:2,c:3"

# Test cascade with different values
curl "http://localhost:8080/amt-param/get?job=TestJob&params=env:dev"
curl "http://localhost:8080/amt-param/get?job=TestJob&params=env:prod"
```

### Compare with Jenkins UI
1. Mở màn "Build with Parameters" trong Jenkins UI
2. Inspect network requests
3. So sánh với output của plugin
4. Đảm bảo choices giống nhau
