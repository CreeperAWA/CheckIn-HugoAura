# 机器人 WebSocket API 文档

## 1. 简介

本API设计用于群聊机器人等服务端应用与客户端之间的实时双向通信，基于WebSocket协议。该API与用户使用的API区分开来，支持多机器人连接，并提供黑名单管理、QQ号验证和消息通知等功能。

## 2. 连接与认证

### 2.1 连接URL

```
{protocol}://{host}:{port}/api/websocket/thirdParty/{sid}
```

- `protocol`: 协议，根据服务器配置选择 `ws` 或 `wss`
- `host`: 服务器主机地址
- `port`: 服务器端口
- `sid`: 机器人唯一标识符（UUID格式）

**注意**：在生产环境中建议使用WSS协议确保数据传输安全，测试环境或无SSL证书的环境可使用WS协议

### 2.2 认证流程

1. 客户端建立WebSocket连接时，通过URL参数提供`sid`（UUID）
2. 连接建立后，客户端立即发送包含JWT Token的认证消息
3. 服务端验证Token，验证通过后建立正式连接

### 2.3 认证消息

#### 客户端发送认证消息

```json
{
  "type": "token",
  "messageId": "550e8400-e29b-41d4-a716-446655440000",
  "data": {
    "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
  }
}
```

#### 服务端发送认证结果

```json
{
  "type": "success",
  "messageId": "550e8400-e29b-41d4-a716-446655440000"
}
```

## 3. 消息格式

所有WebSocket消息采用JSON格式，包含以下字段：

```json
{
  "type": "消息类型",
  "messageId": "消息唯一标识符",
  "data": {
    // 消息数据
  }
}
```

- `type`: 消息类型，字符串
- `messageId`: 消息唯一标识符，UUID格式
- `data`: 消息数据，JSON对象

### 3.1 时间格式

服务端返回的时间数据采用数组格式，具体结构如下：

```json
[年, 月, 日, 时, 分, 秒, 纳秒]
```

- 年：4位数字，如2026
- 月：1-12的数字，如4表示4月
- 日：1-31的数字
- 时：0-23的数字
- 分：0-59的数字
- 秒：0-59的数字（可选，默认为0）
- 纳秒：0-999999999的数字（可选，默认为0）

**示例**：
- `[2026, 4, 16, 10, 30, 0, 0]` 表示2026年4月16日10时30分0秒0纳秒

**前端解析示例**：
```javascript
const formatTime = (timeStr) => {
  if (!timeStr) return "";
  try {
    let date;
    if (Array.isArray(timeStr) && timeStr.length >= 5) {
      const year = timeStr[0];
      const month = timeStr[1] - 1; // JavaScript月份从0开始
      const day = timeStr[2];
      const hours = timeStr[3];
      const minutes = timeStr[4];
      const seconds = timeStr[5] || 0;
      const nanos = timeStr[6] || 0;
      const milliseconds = Math.floor(nanos / 1000000);
      date = new Date(year, month, day, hours, minutes, seconds, milliseconds);
    } else {
      return timeStr;
    }
    // 格式化日期为本地字符串
    return date.toLocaleString();
  } catch (error) {
    console.error('时间格式化错误:', error);
    return timeStr;
  }
};
```

## 4. 功能API

### 4.1 黑名单管理

#### 4.1.1 完整黑名单列表推送

**触发时机**：初始连接时或客户端请求时

```json
{
  "type": "blacklist_full",
  "messageId": "550e8400-e29b-41d4-a716-446655440000",
  "data": {
    "list": [
      {
        "id": "1",
        "qq": "123456789",
        "reason": "违规行为",
        "created_at": [2021, 5, 20, 12, 33, 19, 0]
      },
      // 更多黑名单条目
    ]
  }
}
```

#### 4.1.2 新增黑名单条目推送

**触发时机**：有新的黑名单条目添加时

```json
{
  "type": "blacklist_add",
  "messageId": "550e8400-e29b-41d4-a716-446655440000",
  "data": {
    "id": "2",
    "qq": "987654321",
    "reason": "恶意攻击",
    "created_at": [2021, 5, 20, 12, 33, 20, 0]
  }
}
```

#### 4.1.3 移除黑名单条目推送

**触发时机**：有黑名单条目被移除时

```json
{
  "type": "blacklist_remove",
  "messageId": "550e8400-e29b-41d4-a716-446655440000",
  "data": {
    "id": "1",
    "qq": "123456789"
  }
}
```

### 4.2 QQ号验证

#### 4.2.1 验证请求

**触发时机**：服务端在试题生成前

```json
{
  "type": "qq_verify_request",
  "messageId": "550e8400-e29b-41d4-a716-446655440000",
  "data": {
    "qq": "123456789",
    "verify_content": "AbCdEfGhIjKl"
  }
}
```

#### 4.2.2 验证响应

**客户端返回验证结果**：

```json
{
  "type": "qq_verify_response",
  "messageId": "550e8400-e29b-41d4-a716-446655440000",
  "data": {
    "qq": "123456789",
    "status": "success", // success, failed, no_need, timeout, cannot_verify
    "message": "验证成功"
  }
}
```

#### 4.2.3 验证配置

**网页后台配置**：

- **验证功能开关**：可在网页后台启用/禁用 QQ 号验证机制
- **验证内容生成规则**：
  - 当启用验证且未配置自定义列表时，系统自动生成12位包含大小写英文字母的随机字符串
  - 支持通过后台配置自定义验证字符串列表，字符串可包含中文、英文字符，单个字符串长度不超过99个字符
- **验证有效期**：支持在后台配置验证的有效天数，超过有效期后需要重新验证
- **白名单机制**：支持配置 QQ 号白名单，白名单内的 QQ 号可跳过验证流程（网页界面列表实现参考黑名单页面）

**验证流程**：
1. 服务端在试题生成前，通过WS API向客户端发送包含用户 QQ 号和验证内容的验证请求
2. 客户端处理策略：
   - 验证成功：客户端返回验证成功消息，服务端生成试题并开始答题流程
   - 验证失败：客户端返回验证失败消息，服务端提示"未能通过 QQ 号验证"，不生成试题
   - 无需验证：客户端返回无需验证消息，服务端不弹出验证界面并直接生成题目开始答题
   - 超时处理：客户端在2分钟内未给出回复，服务端根据配置执行相应操作（提示验证超时且不生成试题，或配置为允许答题）
   - 无法验证：客户端发送无法进行验证的消息，服务端根据后台配置执行相应操作（跳过验证生成试题开始考试，或视为验证失败并给予提示）
3. 客户端应实现自动超时处理机制，在用户未能手动发送验证消息时自动发送验证失败消息至服务端

### 4.3 消息通知

**配置说明**：每项通知支持独立启用/禁用配置

#### 4.3.1 同一QQ号短时间多次提交试题

**配置项**：监控时间窗口（分钟）和提交次数阈值
**网页显示格式**："同一 QQ 号_______分钟内提交_______次试题-关/开"

```json
{
  "type": "notification_submit_frequency",
  "messageId": "550e8400-e29b-41d4-a716-446655440000",
  "data": {
    "qq": "123456789",
    "time_window": 5, // 分钟
    "submit_count": 3,
    "start_time": [2021, 5, 20, 12, 33, 19, 0],
    "end_time": [2021, 5, 20, 12, 33, 20, 0]
  }
}
```

#### 4.3.2 用户多次尝试登录后台失败

**配置项**：失败次数阈值
**网页显示格式**："用户_______次登录后台失败-关/开"

```json
{
  "type": "notification_login_failure",
  "messageId": "550e8400-e29b-41d4-a716-446655440000",
  "data": {
    "fail_count": 3,
    "username": "user1",
    "password": "******",
    "fail_time": [2021, 5, 20, 12, 33, 19, 0]
  }
}
```
注意：服务端需要记录前三次的失败信息，包括用户名、密码和失败时间，在达到阈值后触发通知，单独发送每次失败信息。

#### 4.3.3 用户登录后台成功

```json
{
  "type": "notification_login_success",
  "messageId": "550e8400-e29b-41d4-a716-446655440000",
  "data": {
    "username": "user1",
    "qq": "123456789",
    "login_time": [2021, 5, 20, 12, 33, 20, 0],
    "permission_group": "admin"
  }
}
```

#### 4.3.4 用户生成试题后短时间提交

**配置项**：时间阈值（分钟）
**网页显示格式**："用户生成试题后_______分钟内提交-关/开"

```json
{
  "type": "notification_quick_submit",
  "messageId": "550e8400-e29b-41d4-a716-446655440000",
  "data": {
    "qq": "123456789",
    "generate_time": [2021, 5, 20, 12, 33, 15, 0],
    "submit_time": [2021, 5, 20, 12, 33, 20, 0],
    "interval": 5 // 秒
  }
}
```

#### 4.3.5 用户提交试卷

```json
{
  "type": "notification_paper_submit",
  "messageId": "550e8400-e29b-41d4-a716-446655440000",
  "data": {
    "generate_time": [2021, 5, 20, 12, 31, 40, 0],
    "submit_time": [2021, 5, 20, 12, 33, 20, 0],
    "paper_id": "paper_001",
    "qq": "123456789",
    "rating_id": "rating_001",
    "score": 90
  }
}
```

#### 4.3.6 用户开始考试

```json
{
  "type": "notification_exam_start",
  "messageId": "550e8400-e29b-41d4-a716-446655440000",
  "data": {
    "generate_time": [2021, 5, 20, 12, 31, 40, 0],
    "paper_id": "paper_001",
    "qq": "123456789"
  }
}
```

## 5. 错误处理

服务端可能发送的错误消息：

```json
{
  "type": "error",
  "messageId": "550e8400-e29b-41d4-a716-446655440000",
  "data": "错误描述"
}
```

常见错误：
- 认证失败
- 无效消息格式
- 权限不足
- 服务器内部错误

## 6. 示例

### 6.1 完整连接认证流程

1. 客户端连接：`{protocol}://example.com:8080/api/websocket/thirdParty/550e8400-e29b-41d4-a716-446655440000`
2. 客户端发送认证消息（包含JWT Token）
3. 服务端发送认证成功结果
4. 服务端推送完整黑名单列表
5. 后续根据事件推送相关通知

### 6.2 QQ号验证流程

1. 服务端发送验证请求
2. 客户端处理验证（可能需要用户交互）
3. 客户端返回验证结果
4. 服务端根据验证结果执行相应操作

## 7. 注意事项

1. 客户端应实现自动重连机制，确保连接稳定性
2. 客户端应实现消息超时处理，特别是QQ号验证的2分钟超时
3. 服务端应支持多机器人同时连接，通过`sid`区分不同机器人
4. 所有消息应采用JSON格式，确保数据结构的一致性
5. 客户端在连接异常断开后应当进行重试，第一次间隔 5 秒后重试，第二次间隔 10 秒后进行重试，第三次间隔 1 分钟后进行重试，后续每间隔 5 分钟进行 1 次重试