<img width="64" height="64" src="icon.svg" alt="icon">

# CheckIn
#### 简单易用的入群测试系统
![Static Badge](https://img.shields.io/badge/Java-21-red?style=for-the-badge)
![Static Badge](https://img.shields.io/badge/Status-Maintaining-green?style=for-the-badge)
---

### 简介

`CheckIn` 是一个 Web 入群测试系统，专为需要设置入群门槛的群聊设计，通过入群测试确保群内成员水平合格。

---
### 功能特性

#### 记录管理
- **答题记录**
  - 记录每一次答题情况，包括：
    - QQ 号码（由答题者输入，不校验真实性）
    - 头像（根据 QQ 号自动获取）
    - 生成时间、过期时间、提交时间
    - 抽取到的题目、提交的数据
    - 分数和结果

- **请求记录**
  - 记录每一个请求的详细信息：
    - Request headers 和 attributes
    - Response headers
    - SessionID
    - IP 地址（支持 IPv4 和 IPv6）
    - 潜在的异常信息和堆栈

#### 自定义内容
- 支持自定义首页及结果内容，使用 Markdown 格式

#### 分区题库
- 简单易用，功能强大
- 支持自定义题目内容限制（字数、图片、选项等）
- 题目内容支持 Markdown
- 支持单个拖拽、批量操作
- 题目与分区为多对多关系
- 目前提供选择题、题组类型
  - 未来计划加入填空题

#### 动态生成试题
- 根据用户选择的分区、必选分区（可配置选择范围）动态生成试题
- 可配置抽取策略以及补全策略
- 可配置必选分区、可选分区
- 可配置某些分区的特殊限制
- 可配置用户注册策略

#### 自动校验
- 根据答题者的答案自动校验，无需人工干预
- 可自定义任意多个等级，配置不同返回信息
- 可配置评分方式

##### 倒扣分制
倒扣分制是一种评分机制，允许在用户选择错误选项时扣除相应分数，而不仅仅是不得分。这种机制可以有效防止用户随机猜测答案，提高测试的准确性和公平性。

**核心配置**：
- 在管理后台的「评级设置」页面中，通过「启用倒扣分制」开关控制是否开启该功能
- 系统提供多种评分策略，每种策略对错误选项的处理方式不同

**评分策略**：
| 评分策略 | 描述 | 计算方式 | 适用场景 |
|---------|------|---------|---------|
| 全部正确才可得分 | 只有选择所有正确选项且未选择错误选项时才得分 | 全对得满分，否则得 0 分 | 要求严格掌握知识点的场景 |
| 错误时不得分，部分正确时按正确选项占比 | 选择错误选项时不得分，只选择部分正确选项时按比例得分 | 有错误选项得 0 分，否则按正确选项比例得分 | 鼓励用户只选择确定的正确选项 |
| 按正确选项和错误选项占比 | 正确选项和错误选项分别计算比例，得分 = 正确比例 - 错误比例 | 得分 = (正确选项数/总正确选项数) - (错误选项数/总错误选项数) | 平衡奖励正确和惩罚错误的场景 |
| 按正确选项占比和错误双倍占比 | 正确选项按比例得分，错误选项按双倍比例扣分 | 得分 = (正确选项数/总正确选项数) - 2 * (错误选项数/总错误选项数) | 严格惩罚错误选择的场景 |

**计算逻辑**：
1. **基础分数**：每个题目的基础分数由「生成设置」中的「每题分值」决定
2. **得分率计算**：根据选择的评分策略计算得分率（0 到 1 之间的小数，可能为负数）
3. **分数计算**：
   - 启用倒扣分制：最终得分 = 基础分数 × 得分率（可能为负数）
   - 未启用倒扣分制：最终得分 = 基础分数 × max(得分率, 0)（确保得分不为负）

#### 多用户和权限控制
- 支持多用户，可自由添加和删除用户，供群友共同贡献题库
- 通过用户组控制用户权限，包括：
  - 删改自己或他人的题目
  - 添加分区
  - 修改设置
  - 用户管理

#### QBot API
- 可使用 QBot 接入 RestAPI 以实现自动进群审核
---

### 核心特色

- **安全防作弊**：动态生成答题，隐藏题目ID，有效防止刷题和爆破题库
- **灵活分区**：分区答题系统，自由度高，可根据不同需求设置不同难度和内容
- **协作共建**：多用户支持，权限控制细化，更改冲突提醒，便于群友共同建设题库
- **分层评估**：多分数级别支持，可根据分数划分不同层次，提供个性化反馈
- **易于配置**：设置多样，后台管理页面配置简易，可自定义程度高
- **实时更新**：后台管理页面采用 WebSocket 通信，实现实时数据更新
- **响应式设计**：前端适配各种尺寸的设备，确保在不同终端上的良好体验

---

<details>

<summary>
截图
</summary>

![1.png](/screenshots/2.0.1/screenshot(1).jpeg "1")
![2.png](/screenshots/2.0.1/screenshot(2).jpeg "2")
![3.png](/screenshots/2.0.1/screenshot(3).jpeg "3")
![4.png](/screenshots/2.0.1/screenshot(4).jpeg "4")
![5.png](/screenshots/2.0.1/screenshot(5).jpeg "5")
![6.png](/screenshots/2.0.1/screenshot(6).jpeg "6")
![7.png](/screenshots/2.0.1/screenshot(7).jpeg "7")
![8.png](/screenshots/2.0.1/screenshot(8).jpeg "8")
![9.png](/screenshots/2.0.1/screenshot(9).jpeg "9")
![10.png](/screenshots/2.0.1/screenshot(10).jpeg "10")
![11.png](/screenshots/2.0.1/screenshot(11).jpeg "11")
![12.png](/screenshots/2.0.1/screenshot(12).jpeg "12")
![13.png](/screenshots/2.0.1/screenshot(13).jpeg "13")
![14.png](/screenshots/2.0.1/screenshot(14).jpeg "14")
![15.png](/screenshots/2.0.1/screenshot(15).jpeg "15")
![16.png](/screenshots/2.0.1/screenshot(16).jpeg "16")
![17.png](/screenshots/2.0.1/screenshot(17).jpeg "17")
![18.png](/screenshots/2.0.1/screenshot(18).jpeg "18")
![19.png](/screenshots/2.0.1/screenshot(19).jpeg "19")
![20.png](/screenshots/2.0.1/screenshot(20).jpeg "20")
![21.png](/screenshots/2.0.1/screenshot(21).jpeg "21")
![22.png](/screenshots/2.0.1/screenshot(22).jpeg "22")
![23.png](/screenshots/2.0.1/screenshot(23).jpeg "23")
![24.png](/screenshots/2.0.1/screenshot(24).jpeg "24")
![25.png](/screenshots/2.0.1/screenshot(25).jpeg "25")

</details>

---

### 技术栈
- **后端**：SpringBoot 和 Spring 系列框架
- **前端**：Vue 等

---

### 试运行（DEMO）
无需任何配置，直接使用如下命令运行：

```shell
java -jar checkIn-x.x.x.jar
```

**预期行为**：系统将在内存中创建临时数据库，关闭服务器后自动销毁

> 若需体验常规存储模式（文件模式），请参考下文搭建教程中的更换数据库步骤

**访问信息**：
- 管理入口：详见下方 API 及端点部分
- **初始用户**：super admin 或 10000
- **密码**：114514

---

### 搭建教程

#### 步骤 1：准备数据库
- 连接 MySQL 数据库并创建 `check_in` 数据库

#### 步骤 2：配置应用
在 jar 包所在位置创建 `application.properties` 文件，并填入以下内容：

```properties
spring.datasource.url=jdbc:mysql://localhost:3306/check_in?useUnicode=true&characterEncoding=utf-8&useSSL=false&serverTimezone=Asia/Shanghai
spring.datasource.username=[your username]
spring.datasource.password=[your password]
```

> 由于添加了 demo 支持，需要手动指定数据库连接信息

#### 步骤 3：运行应用
- 执行以下命令运行 jar 包，系统会自动生成数据库表结构及初始数据
  ```shell
  java -jar checkIn-x.x.x.jar
  ```

**注意事项**：
- 需要 Java 21 运行环境
- 命令行主目录需要在 jar 包所在目录下
  - 以 cmd 为例，若 jar 包在 `C:\checkIn\checkIn-x.x.x.jar` 下，则需要先执行：
    ```shell
    cd C:\checkIn\
    ```

**访问信息**：
- **初始用户**：super admin
- **密码**：114514

#### 数据库配置选项

<details>
<summary>如何更换数据库名或数据库服务器</summary>

修改 `application.properties` 中的 `spring.datasource.url` 配置：

```properties
spring.datasource.url=jdbc:mysql://[server]:[port]/[database]?useUnicode=true&characterEncoding=utf-8&useSSL=false&serverTimezone=Asia/Shanghai
```

**支持的数据库**：

- **MySQL**
  ```properties
  spring.datasource.url=jdbc:mysql://localhost:3306/check_in?characterEncoding=utf8&allowPublicKeyRetrieval=true&useSSL=false
  ```

- **H2 Database (文件模式)**
  ```properties
  spring.datasource.url=jdbc:h2:file:./check_in
  spring.datasource.username=root
  spring.datasource.password=root
  #=== 开启 Web 控制台 (http://localhost:8080/h2-console) ===
  #spring.h2.console.enabled=true
  #spring.h2.console.path=/h2-console
  ```
</details>


### API 及端点

#### 主要端点
- **答题**：`http://localhost:8080/checkIn/exam/`
- **管理**：`http://localhost:8080/checkIn/manage/` 和 `http://localhost:8080/checkIn/login/`
- **QBot API**：`http://localhost:8080/checkIn/api/qualify/`

#### QBot API 使用说明

**请求方式**：POST

**请求头**：
- `Token`: 在高级设置中生成的 JSON Web Token

**请求体**：
```json
{
    "qq": 123456789
}
```

**响应示例**：

<details>
<summary>失败示例（用户未答题）</summary>

```json 
{
    "result": "examData not found",
    "type": "error"
}
```

</details>

<details>
<summary>成功示例1（答题通过）</summary>

```json
{
    "examData": {
        "id": "4fb9ec45-bffa-402e-92ff-078b993eb303",
        "qqNumber": 10001,
        "questionAmount": 10,
        "status": "SUBMITTED",
        "examResult": {
            "qq": 10001,
            "score": 100.0,
            "correctCount": 10,
            "halfCorrectCount": 0,
            "wrongCount": 0,
            "questionCount": 10,
            "message": "[markdown text1]",
            "level": "通过",
            "levelId": "6aa0dad5-10c5-4d50-9d03-bff7ff669e02",
            "colorHex": "#67C23A",
            "examDataId": "4fb9ec45-bffa-402e-92ff-078b993eb303",
            "showCreatingAccountGuide": false,
            "signUpCompletingType": null
        },
        "generateTime": "2025-02-11 23:03:45",
        "submitTime": "2025-02-11 23:03:58",
        "expireTime": "2025-02-18 23:03:45"
    },
    "level": "通过",
    "levelId": "6aa0dad5-10c5-4d50-9d03-bff7ff669e02",
    "type": "success"
}
```

</details>

<details>
<summary>成功示例2（答题未通过）</summary>

```json
{
    "examData": {
        "id": "9e2b4566-33c5-4279-9b76-0140926f5cab",
        "qqNumber": 10002,
        "questionAmount": 10,
        "status": "SUBMITTED",
        "examResult": {
            "qq": 10002,
            "score": 0.0,
            "correctCount": 0,
            "halfCorrectCount": 0,
            "wrongCount": 10,
            "questionCount": 10,
            "message": "[markdown text2]",
            "level": "未通过",
            "levelId": "3325af76-e7e4-4c8f-a981-8f4c35e8261b",
            "colorHex": "#F56C6C",
            "examDataId": "9e2b4566-33c5-4279-9b76-0140926f5cab",
            "showCreatingAccountGuide": false,
            "signUpCompletingType": null
        },
        "generateTime": "2025-02-11 23:05:54",
        "submitTime": "2025-02-11 23:06:03",
        "expireTime": "2025-02-18 23:05:54"
    },
    "level": "未通过",
    "levelId": "3325af76-e7e4-4c8f-a981-8f4c35e8261b",
    "type": "success"
}
```

</details>

#### 字段说明

**通用字段**：
- `type`: `"success"` | `"error"` - 响应类型

**成功响应时的字段**：

**部分值可能相同的字段**：
- `examData.qqNumber`, `examData.examResult.qq`: 均为 QQ 号（获取未完成的测试记录时，`examData.examResult.qq` 为 -1）
- `examData.questionAmount`, `examData.examResult.questionCount`: 均为抽取题目数量（获取未完成的测试记录时，`examData.examResult.questionCount` 为 -1）
- `examData.id`, `examData.result.examDataId`: 均为答题记录的内部 ID（获取未完成的测试记录时，`examData.result.examDataId` 为 null）
- `level`, `examData.result.level`: 均与评级设置中的不同等级名称对应（获取未完成的测试记录时为 null）
- `levelId`, `examData.result.levelId`: 均与评级设置中的不同等级 ID 对应（机器人判断应优先使用该字段，获取未完成的测试记录时为 null）

**枚举字段**：
- `examData.status`: 
  - `"ONGOING"` (进行中) 
  - `"SUBMITTED"` (已提交) 
  - `"MANUAL_INVALIDED"` (已手动无效) 
  - `"EXPIRED"` (已过期，7天) 
  - `"SIGN_UP_COMPLETED"` (已注册完成，需在评级设置中设置注册方式)
  - `"SCORE_INVALIDED"` (成绩已无效化，通过手动操作将成绩标记为无效)

- `examData.examResult.signUpCompletingType`: 
  - `"USER_EXISTS"` (用户已存在) 
  - `"INCOMPLETED"` (未完成) 
  - `"INSPECT_REQUIRED"` (需通过 Qualify API 审查，但 Qualify API 被调用时状态变更为 COMPLETED，因此一般无法出现) 
  - `"COMPLETED"` (完成)
#### 如何修改默认端口

在 `application.properties` 文件中添加以下配置：

```properties
server.port=[your port]
```
