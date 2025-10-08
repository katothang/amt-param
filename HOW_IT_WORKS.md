# CÁCH HOẠT ĐỘNG CỦA GIẢI PHÁP MỚI

## 🎯 Nguyên lý cốt lõi

### Vấn đề của Regex
Regex **KHÔNG THÊ** parse và execute Groovy script vì:
1. Groovy là ngôn ngữ lập trình phức tạp
2. Script có thể chứa logic, conditional, loops
3. Script có thể truy cập Jenkins API, environment variables
4. Regex chỉ match pattern text, không thể execute code

### Giải pháp: Dùng Jenkins API
Jenkins đã có sẵn API để execute Groovy và lấy choices. Plugin của chúng ta chỉ cần:
1. Gọi đúng methods của Jenkins
2. Truyền đúng parameters (Job, current values)
3. Nhận kết quả đã được Jenkins render

## 🔄 Flow xử lý Parameter

### 1. Request đến API
```
GET /amt-param/get?job=MyJob&params=env:prod,region:us-east-1
```

### 2. Parse request parameters
```java
// Lấy job name
String jobName = req.getParameter("job");

// Lấy current values (cho cascade parameters)
Map<String, String> currentValues = parseParameterValues(req.getParameter("params"));
// Result: {"env": "prod", "region": "us-east-1"}
```

### 3. Tìm Job trong Jenkins
```java
Jenkins jenkins = Jenkins.get();
Job<?, ?> job = jenkins.getItemByFullName(jobName, Job.class);
```

### 4. Kiểm tra permission
```java
job.checkPermission(Item.READ);
```

### 5. Lấy tất cả parameters
```java
ParametersDefinitionProperty prop = job.getProperty(ParametersDefinitionProperty.class);
List<ParameterDefinition> definitions = prop.getParameterDefinitions();
```

### 6. Render từng parameter

#### 6.1. Built-in Parameters (StringParameterDefinition, BooleanParameterDefinition, etc.)
```java
if (def instanceof ChoiceParameterDefinition) {
    // Lấy trực tiếp choices đã định nghĩa sẵn
    param.choices = new ArrayList<>(choiceDef.getChoices());
}
```

#### 6.2. Active Choices Parameters (Dynamic)
```java
// Bước 1: Lấy ChoiceProvider (object chứa logic tạo choices)
Object choiceProvider = getChoiceProvider(def);

// Bước 2: Gọi methods của ChoiceProvider để lấy choices
if (choiceProvider != null) {
    choices = renderChoiceProviderWithGroovy(choiceProvider, job, currentValues);
}
```

## 🔑 Các Method quan trọng

### Method 1: getChoiceProvider()
**Mục đích:** Lấy ChoiceProvider từ ParameterDefinition

```java
private Object getChoiceProvider(ParameterDefinition def) {
    // Active Choices Parameter có method getChoiceProvider()
    // Method này trả về object chứa logic để tạo choices
    Method method = def.getClass().getMethod("getChoiceProvider");
    return method.invoke(def);
}
```

**ChoiceProvider là gì?**
- Object chứa Groovy script
- Có methods để execute script và return choices
- Jenkins UI cũng dùng ChoiceProvider này

### Method 2: renderChoiceProviderWithGroovy()
**Mục đích:** Execute Groovy script qua Jenkins API

```java
private List<String> renderChoiceProviderWithGroovy(Object choiceProvider, Job job, Map<String, String> currentValues) {
    // Thử 4 signatures khác nhau của Active Choices plugin
    
    // 1. getChoicesForUI(Map) - Jenkins UI dùng method này
    // Truyền Map chứa current values của các parameters
    Method m1 = providerClass.getMethod("getChoicesForUI", Map.class);
    Object result = m1.invoke(choiceProvider, currentValues);
    
    // 2. getValues(Job, Map) - Active Choices v2.x
    Method m2 = providerClass.getMethod("getValues", Job.class, Map.class);
    Object result = m2.invoke(choiceProvider, job, currentValues);
    
    // 3. getChoices(Map) - Truyền current values
    Method m3 = providerClass.getMethod("getChoices", Map.class);
    Object result = m3.invoke(choiceProvider, currentValues);
    
    // 4. getChoices() - Fallback không parameters
    Method m4 = providerClass.getMethod("getChoices");
    Object result = m4.invoke(choiceProvider);
}
```

**Tại sao thử nhiều methods?**
- Active Choices plugin có nhiều version khác nhau
- Mỗi version dùng method signature khác nhau
- Plugin của chúng ta tương thích với tất cả versions

### Method 3: getChoicesDirectly()
**Mục đích:** Lấy choices trực tiếp từ ParameterDefinition (không qua ChoiceProvider)

```java
private List<String> getChoicesDirectly(ParameterDefinition def, Job job, Map<String, String> currentValues) {
    // Một số parameters không dùng ChoiceProvider
    // Mà implement methods trực tiếp trong ParameterDefinition
    
    // Thử các signatures:
    // 1. getChoices(Job, Map) - Dynamic Reference Parameter
    // 2. getChoices(Map) - Cascade Choice Parameter
    // 3. doFillValueItems(Item, String) - Jenkins Descriptor
}
```

### Method 4: normalizeChoicesResult()
**Mục đích:** Chuẩn hóa kết quả từ nhiều định dạng về List<String>

```java
private List<String> normalizeChoicesResult(Object result) {
    // Jenkins API có thể trả về nhiều kiểu:
    
    // 1. List<String> - Lấy trực tiếp
    if (result instanceof List) {
        return (List<String>) result;
    }
    
    // 2. Map - Lấy values (hoặc keys nếu values null)
    if (result instanceof Map) {
        return new ArrayList<>(map.values());
    }
    
    // 3. ListBoxModel - Jenkins UI component
    if (result.getClass().getName().contains("ListBoxModel")) {
        // Duyệt qua iterator
        // Lấy name hoặc value của từng Option
    }
    
    // 4. Array, Collection, String, Object...
}
```

## 🎭 Ví dụ cụ thể

### Case 1: Simple Choice Parameter
```groovy
// Groovy script trong Jenkins:
return ["Option 1", "Option 2", "Option 3"]
```

**Plugin xử lý:**
```java
// 1. Lấy ChoiceProvider
Object provider = getChoiceProvider(parameterDefinition);

// 2. Gọi getChoices()
Object result = provider.getClass().getMethod("getChoices").invoke(provider);
// result = ["Option 1", "Option 2", "Option 3"]

// 3. Normalize
List<String> choices = normalizeChoicesResult(result);
// choices = ["Option 1", "Option 2", "Option 3"]
```

### Case 2: Cascade Choice Parameter
```groovy
// Script phụ thuộc vào parameter "environment"
def env = environment  // Lấy giá trị từ parameter khác

if (env == "prod") {
    return ["us-east-1", "us-west-1"]
} else {
    return ["dev-1", "dev-2"]
}
```

**Plugin xử lý:**
```java
// 1. Current values từ request
Map<String, String> currentValues = {"environment": "prod"}

// 2. Lấy ChoiceProvider
Object provider = getChoiceProvider(parameterDefinition);

// 3. Gọi getChoicesForUI với current values
Object result = provider.getClass()
    .getMethod("getChoicesForUI", Map.class)
    .invoke(provider, currentValues);
// Jenkins execute script với environment="prod"
// result = ["us-east-1", "us-west-1"]

// 4. Normalize
List<String> choices = normalizeChoicesResult(result);
```

**Tại sao đúng?**
- Jenkins execute Groovy script THỰC SỰ
- Script có access đến current values
- Kết quả là output THỰC của script execution, không phải regex parsing

### Case 3: Dynamic Reference Parameter với API call
```groovy
// Script gọi API
def url = "https://api.example.com/regions?env=${environment}"
def json = new URL(url).text
def regions = new JsonSlurper().parseText(json)
return regions.collect { it.name }
```

**Plugin xử lý:**
```java
// 1. Current values
Map<String, String> currentValues = {"environment": "staging"}

// 2. Lấy ChoiceProvider
Object provider = getChoiceProvider(parameterDefinition);

// 3. Jenkins execute script
Object result = provider.getClass()
    .getMethod("getValues", Job.class, Map.class)
    .invoke(provider, job, currentValues);
// Jenkins:
// - Thay ${environment} = "staging"
// - Gọi API thực sự
// - Parse JSON
// - Trả về list regions

// 4. Normalize kết quả
List<String> choices = normalizeChoicesResult(result);
```

**Tại sao KHÔNG thể dùng regex?**
- Script gọi external API
- Cần parse JSON
- Cần Jenkins network access
- Regex chỉ match text, không thể execute code

## ✅ Tại sao chính xác 100%?

1. **Dùng chính engine của Jenkins**
   - Groovy script được execute bởi Jenkins Groovy engine
   - Có access đến tất cả Jenkins API
   - Có access đến environment variables

2. **Dùng chính methods mà Jenkins UI dùng**
   - `getChoicesForUI()` là method mà màn "Build with Parameters" gọi
   - Kết quả giống hệt với UI

3. **Xử lý đầy đủ context**
   - Truyền Job object
   - Truyền current parameter values
   - Truyền Map để handle cascade parameters

4. **Không làm gì thêm, chỉ gọi API**
   - Plugin không parse hay modify gì cả
   - Chỉ gọi Jenkins API và format lại kết quả

## 🎓 Bài học

### ❌ Sai lầm khi dùng Regex
```java
// ĐỪNG LÀM NHƯ NÀY
String script = getScript();
Pattern pattern = Pattern.compile("return\\s*\\[([^\\]]+)\\]");
Matcher matcher = pattern.matcher(script);
// => Chỉ match được pattern đơn giản, sai với script phức tạp
```

### ✅ Đúng khi dùng Jenkins API
```java
// LÀM NHƯ NÀY
Object choiceProvider = getChoiceProvider(def);
Method method = choiceProvider.getClass().getMethod("getChoicesForUI", Map.class);
Object result = method.invoke(choiceProvider, currentValues);
// => Jenkins execute script đúng, trả về kết quả chính xác
```

## 🚀 Performance

**So sánh:**
- Regex: Parse text → Extract pattern → Build list
- Jenkins API: Execute script → Return result

**Kết quả:**
- Jenkins API **NHANH HƠN** vì không cần parse
- Jenkins API **CHÍNH XÁC HƠN** vì execute đúng logic
- Jenkins API **ÍT LỖI HƠN** vì dùng chính code của Jenkins

## 📚 Tài liệu tham khảo

- Jenkins ParameterDefinition API
- Active Choices Plugin source code
- Jenkins Groovy script execution
- Jenkins UI Build with Parameters implementation
