# AMT integration - Jenkins Plugin để lấy thông tin Parameters
## ✨ Tính năng chính

- ✅ **100% chính xác** - Kết quả giống hệt Jenkins UI "Build with Parameters"
- ✅ **Không dùng regex** - Sử dụng Jenkins API chính thức thay vì regex parsing
- ✅ **Hỗ trợ đầy đủ** - Tất cả loại parameters (built-in và Active Choices)
- ✅ **Cascade parameters** - Xử lý đúng parameters phụ thuộc vào nhau

## 🚀 Cài đặt

1. Vào Jenkins UI: **Manage Jenkins** > **Manage Plugins** > **Advanced**
2. Upload file `amt-integration.hpi`
3. Restart Jenkins

## 📖 Sử dụng

### Job API Endpoint (Khuyến nghị - v1.0.3+)

Cách đơn giản nhất để lấy thông tin parameters:

```bash
GET {JENKINS_URL}/job/{JOB_NAME}/amt-integration/api?params=param1:value1,param2:value2
```

**Ví dụ:**
```bash
# Lấy parameters của job AMT_param với Channel=C01
curl "https://jenkins.thangnotes.dev/job/AMT_param/amt-integration/api?params=Channel:C01"

# trong đó params là giá trị mà người dùng đã nhập trên màn hình AMT nó dùng làm điều kiện để ẩn hiện các param khác nếu có 


# Lấy tất cả parameters (không có current values)
curl "https://jenkins.thangnotes.dev/job/AMT_param/amt-integration/api"

# Lấy parameters của job trong folder
curl "https://jenkins.thangnotes.dev/job/MyFolder/job/MyJob/amt-integration/api?params=param1:value1,param2:value2"
``` 
**FORMAT DATA TRẢ VỀ :**
```json
{
  "success": true,
  "data": {
    "jobName": "AMT_NEW_JOB",
    "jobFullName": "AMT_LIVE/AMT_FOLDER/AMT_NEW_JOB",
    "jobUrl": "job/AMT_LIVE/job/AMT_FOLDER/job/AMT_NEW_JOB/",
    "buildWithParametersUrl": "job/AMT_LIVE/job/AMT_FOLDER/job/AMT_NEW_JOB/buildWithParameters",
    "activeChoicesPluginAvailable": true,
    "activeChoicesPluginVersion": "2.8.8",
    "parameters": [
      {
        "name": "Channel",
        "type": "ChoiceParameter",
        "description": "Chọn kênh cho build. Nếu chọn 'C01' thì tham số 'depen' sẽ hiển thị 'D01'; nếu chọn 'C02' thì tham số 'depen' sẽ hiển thị 'D02'. Điều này giúss bạn dễ test vì các giá trị gần nhau.",
        "currentValue": "C01",
        "inputType": "select",
        "isDynamic": true,
        "isRequired": false,
        "errorMessage": null,
        "dependencies": [],
        "choices": [
          "C01",
          "C02"
        ],
        "data": null
      },
      {
        "name": "depen",
        "type": "CascadeChoiceParameter",
        "description": "Danh sách giá trị của tham số này phụ thuộc vào Channel. Nếu Channel = 'C01', tham số sẽ hiển thị hai checkbox OptionA và OptionB. Nếu Channel là 'C02' hoặc giá trị khác, tham số sẽ hiển thị ba checkbox OptionA, OptionB và OptionC. Bạn có thể chọn một hoặc nhiều tùy chọn.",
        "currentValue": "OptionB",
        "inputType": "select",
        "isDynamic": true,
        "isRequired": false,
        "errorMessage": null,
        "dependencies": [
          "Channel"
        ],
        "choices": [
          "OptionA",
          "OptionB"
        ],
        "data": null
      },
      {
        "name": "golive",
        "type": "DynamicReferenceParameter",
        "description": "Nếu depen option A thì hiện không thì ẩn",
        "currentValue": "",
        "inputType": "dynamic_reference",
        "isDynamic": true,
        "isRequired": false,
        "errorMessage": null,
        "dependencies": [
          "depen"
        ],
        "choices": [],
        "data": "\n<input type=\"hidden\" name=\"value\" value=\"\" />\n\n"
      },
      {
        "name": "customString",
        "type": "StringParameterDefinition",
        "description": "A sample string parameter for custom messages.",
        "currentValue": "defaultValue",
        "inputType": "text",
        "isDynamic": false,
        "isRequired": false,
        "errorMessage": null,
        "dependencies": [],
        "choices": [],
        "data": null
      },
      {
        "name": "runFlag",
        "type": "BooleanParameterDefinition",
        "description": "Sample boolean flag to enable or disable a feature.",
        "currentValue": "false",
        "inputType": "checkbox",
        "isDynamic": false,
        "isRequired": false,
        "errorMessage": null,
        "dependencies": [],
        "choices": [
          "true",
          "false"
        ],
        "data": null
      }
    ]
  }
}
``` 
![alt text](image.png)
