# AMT Parameters API - Array Values Support

## Tổng quan

Plugin AMT Parameters API đã được cập nhật để hỗ trợ **multiple values** cho checkbox parameters và các parameter type khác yêu cầu nhiều giá trị.

## Format URL mới

### Single Value Parameter
```
Channel:C01
```

### Array Value Parameter (Multiple Values)
```
depen:[OptionB,OptionA]
```

### Mixed Parameters
```
Channel:C01,depen:[OptionB,OptionA],Environment:DEV
```

## Ví dụ sử dụng

### 1. Single Checkbox Value (Legacy - vẫn hoạt động)
```
GET /job/AMT_LIVE/job/AMT_FOLDER/job/AMT_NEW_JOB/amt-integration/api?params=Channel:C01,depen:OptionA
```

**Response:**
```json
{
  "success": true,
  "data": {
    "jobName": "AMT_NEW_JOB",
    "parameters": [
      {
        "name": "Channel",
        "currentValue": "C01"
      },
      {
        "name": "depen",
        "currentValue": "OptionA"
      }
    ]
  }
}
```

### 2. Multiple Checkbox Values (NEW)
```
GET /job/AMT_LIVE/job/AMT_FOLDER/job/AMT_NEW_JOB/amt-integration/api?params=Channel:C01,depen:[OptionB,OptionA]
```

**Response:**
```json
{
  "success": true,
  "data": {
    "jobName": "AMT_NEW_JOB",
    "parameters": [
      {
        "name": "Channel",
        "currentValue": "C01"
      },
      {
        "name": "depen",
        "currentValue": "OptionB,OptionA"
      }
    ]
  }
}
```

**Lưu ý:** Các giá trị trong array sẽ được trả về dưới dạng comma-separated string (`"OptionB,OptionA"`)

### 3. Complex Example với nhiều parameters
```
GET /job/AMT_LIVE/job/AMT_FOLDER/job/AMT_NEW_JOB/amt-integration/api?params=Channel:C01,depen:[OptionB,OptionA],features:[Feature1,Feature2,Feature3],Environment:PROD
```

**Response:**
```json
{
  "success": true,
  "data": {
    "jobName": "AMT_NEW_JOB",
    "parameters": [
      {
        "name": "Channel",
        "currentValue": "C01"
      },
      {
        "name": "depen",
        "currentValue": "OptionB,OptionA"
      },
      {
        "name": "features",
        "currentValue": "Feature1,Feature2,Feature3"
      },
      {
        "name": "Environment",
        "currentValue": "PROD"
      }
    ]
  }
}
```

## Chi tiết kỹ thuật

### Parse Logic

Logic parse parameters đã được cập nhật để:

1. **Detect array syntax:** Khi gặp `[...]`, parser nhận biết đây là array value
2. **Extract array content:** Lấy nội dung bên trong `[]` (ví dụ: `OptionB,OptionA`)
3. **Store as comma-separated string:** Lưu các giá trị dưới dạng string với dấu phẩy ngăn cách
4. **Backward compatible:** Single values không có `[]` vẫn hoạt động như cũ

### Code Changes

**Trước đây (v1.0.2):**
```java
// Chỉ hỗ trợ single value
private Map<String, String> parseParameterValues(String paramsStr) {
    String[] pairs = paramsStr.split(",");
    // ... parse each pair as key:value
}
```

**Bây giờ (v1.0.3+):**
```java
// Hỗ trợ cả single value và array value
private Map<String, String> parseParameterValues(String paramsStr) {
    // Parse từng parameter
    // Detect array syntax with [...]
    // Handle both single and array values
    // Return comma-separated string for arrays
}
```

### Xử lý Array Values

Khi nhận được array value từ API, bạn có thể split để lấy từng giá trị:

**Java:**
```java
String depenValue = parameters.get("depen"); // "OptionB,OptionA"
String[] values = depenValue.split(",");
// values[0] = "OptionB"
// values[1] = "OptionA"
```

**JavaScript:**
```javascript
const depenValue = parameters.depen; // "OptionB,OptionA"
const values = depenValue.split(',');
// values[0] = "OptionB"
// values[1] = "OptionA"
```

**Python:**
```python
depen_value = parameters['depen']  # "OptionB,OptionA"
values = depen_value.split(',')
# values[0] = "OptionB"
# values[1] = "OptionA"
```

## Edge Cases

### 1. Empty Array
```
param:[]
```
Result: `""`

### 2. Single Value in Array
```
param:[OnlyOne]
```
Result: `"OnlyOne"`

### 3. Array with Spaces
```
options:[Option A,Option B,Option C]
```
Result: `"Option A,Option B,Option C"`

### 4. Malformed Array (Missing Closing Bracket)
```
bad:[value1,value2
```
Parser sẽ xử lý gracefully và treat như regular string

## Compatibility

- ✅ **Backward Compatible:** Single values không có `[]` vẫn hoạt động
- ✅ **Legacy API:** API pattern cũ (`/amt-integration/get?job=...`) vẫn được hỗ trợ
- ✅ **New API:** API pattern mới (`/job/{JOB_NAME}/amt-integration/api?params=...`) được khuyên dùng

## Testing

Plugin đi kèm với comprehensive test suite:

```bash
# Run all tests
mvn test

# Run only parameter parsing tests
mvn test -Dtest=ParameterParsingTest
```

Test coverage:
- ✅ Single value parsing
- ✅ Multiple single values
- ✅ Array value parsing
- ✅ Mixed single and array values
- ✅ Complex scenarios với nhiều arrays
- ✅ Edge cases (empty array, malformed array, etc.)
- ✅ Real-world scenarios

## Troubleshooting

### Problem: Array values không được parse đúng

**Solution:** Đảm bảo format đúng:
- ✅ Correct: `depen:[OptionB,OptionA]`
- ❌ Wrong: `depen: [OptionB, OptionA]` (có spaces)
- ❌ Wrong: `depen:OptionB,OptionA` (thiếu brackets)

### Problem: Giá trị bị cắt ngắn

**Solution:** Đảm bảo URL encoding đúng:
```javascript
const params = encodeURIComponent('Channel:C01,depen:[OptionB,OptionA]');
const url = `/job/JOB_NAME/amt-integration/api?params=${params}`;
```

## Migration Guide

Nếu bạn đang sử dụng plugin version cũ:

### Before (v1.0.2)
```
# Chỉ gửi 1 giá trị
GET /amt-integration/api?params=Channel:C01,depen:OptionA
```

### After (v1.0.3+)
```
# Có thể gửi nhiều giá trị
GET /amt-integration/api?params=Channel:C01,depen:[OptionA,OptionB]

# Legacy format vẫn hoạt động
GET /amt-integration/api?params=Channel:C01,depen:OptionA
```

## API Version

- **Current Version:** 1.0.3+
- **Array Support:** ✅ Enabled
- **Backward Compatible:** ✅ Yes

## Links

- GitHub: https://github.com/katothang/amt-param
- Jenkins Plugin: https://jenkins.thangnotes.dev
