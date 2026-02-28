# Code2Spec LLM 增强方案设计

## 1. 目标

- **业务语义描述**：对接口实现逻辑生成具有良好业务语义的自然语言描述
- **错误码增强**：为错误码提供根因描述和合适的处理建议
- **LLM 集成**：在规则提取基础上，通过 LLM 增强文档质量

## 2. 整体架构

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                          Code2Spec Pipeline                                  │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  ┌──────────────┐    ┌──────────────────┐    ┌──────────────────────────┐  │
│  │ Java 源码    │───▶│ 规则层 (Rule)    │───▶│ 结构化中间模型            │  │
│  │ 仓库        │    │ - AST 解析       │    │ - Endpoint / Schema /     │  │
│  └──────────────┘    │ - 端点提取      │    │   ErrorCode / 依赖关系    │  │
│                      │ - Schema 推导   │    └─────────────┬──────────────┘  │
│                      │ - 错误码映射    │                  │                  │
│                      └──────────────────┘                  ▼                  │
│                                                           ┌──────────────────┐
│                                                           │ LLM 增强层       │
│                                                           │ - 业务语义描述   │
│                                                           │ - 错误码根因与   │
│                                                           │   处理建议       │
│                                                           └────────┬─────────┘
│                                                                    │
│                                                                    ▼
│                      ┌──────────────────────────────────────────────────────┐
│                      │ 输出层                                                │
│                      │ OpenAPI / Markdown / RAG 知识对象                     │
│                      └──────────────────────────────────────────────────────┘
└─────────────────────────────────────────────────────────────────────────────┘
```

## 3. LLM 增强点设计

### 3.1 业务语义描述 (Business Semantic Description)

**输入**（由规则层提供）：
- 接口 URI、HTTP 方法
- 方法签名（参数、返回值类型）
- 方法体代码片段（或关键逻辑摘要）
- Javadoc / 注解中的已有描述
- 调用的 Service / Repository 方法名

**LLM 任务**：生成结构化业务描述，包含：
- **功能概述**：一句话说明接口做什么
- **业务场景**：典型使用场景（如：用户登录、订单创建）
- **实现要点**：关键逻辑、校验规则、依赖服务
- **注意事项**：调用方需注意的事项

**Prompt 设计**：提供代码上下文 + 结构化输出要求（JSON schema）

### 3.2 错误码根因与处理建议 (Error Code Enhancement)

**输入**（由规则层提供）：
- 错误码 / 异常类名
- 抛出该异常的代码上下文
- 异常处理逻辑（@ExceptionHandler 中的处理）
- 相关业务逻辑

**LLM 任务**：生成：
- **根因描述**：导致该错误的典型原因（业务层面）
- **处理建议**：调用方应如何应对（重试、参数修正、联系支持等）
- **预防建议**：如何避免触发该错误

**Prompt 设计**：错误码 + 代码上下文 + 输出格式要求

## 4. LLM 集成策略

### 4.1 接口抽象

```java
public interface LlmEnhancer {
    BusinessSemantic enhanceEndpoint(EndpointContext ctx);
    ErrorCodeEnhancement enhanceErrorCode(ErrorCodeContext ctx);
}
```

### 4.2 实现选项

- **OpenAI API**：gpt-4o / gpt-4o-mini
- **兼容 OpenAI 的 API**：Azure OpenAI、通义千问、DeepSeek 等
- **本地模型**：Ollama、vLLM 等（通过 OpenAI 兼容接口）

### 4.3 配置

- API 端点、API Key（环境变量 / 配置文件）
- 模型选择
- 是否启用 LLM 增强（可关闭，仅用规则输出）
- 批处理与速率限制

## 5. 模块划分

| 模块 | 职责 |
|------|------|
| `core` | 核心模型、接口定义 |
| `parser` | Java AST 解析、端点/Schema/错误码提取 |
| `llm` | LLM 客户端抽象、Prompt 模板、增强逻辑 |
| `export` | OpenAPI、Markdown、RAG 知识对象导出 |
| `cli` | 命令行入口、配置加载 |

## 6. 数据流

```
Java 源码
    │
    ▼
Parser → List<RawEndpoint>, List<RawErrorCode>, Schemas
    │
    ▼
LlmEnhancer (可选)
    │  - enhanceEndpoint() → BusinessSemantic
    │  - enhanceErrorCode() → ErrorCodeEnhancement
    ▼
EnrichedEndpoint, EnrichedErrorCode
    │
    ▼
Exporter → OpenAPI / Markdown / RAG JSON
```

## 7. RAG 知识对象结构

为 Agent 问答优化的结构化输出示例：

```json
{
  "type": "rest_endpoint",
  "uri": "POST /api/v1/orders",
  "summary": "创建订单",
  "business_semantic": {
    "function": "用户提交购物车生成订单",
    "scenario": "结账流程中的订单创建",
    "implementation_notes": "校验库存、计算价格、生成订单号",
    "cautions": "需先登录，购物车不能为空"
  },
  "request_schema": { ... },
  "response_schema": { ... },
  "error_codes": [
    {
      "code": "INSUFFICIENT_STOCK",
      "root_cause": "所选商品库存不足",
      "handling_suggestion": "提示用户减少数量或选择替代商品",
      "prevention": "下单前可调用库存查询接口确认"
    }
  ]
}
```

## 8. 实现顺序

1. 项目骨架、依赖、配置
2. 核心模型与 Parser（规则层）
3. LLM 客户端与 Prompt 模板
4. 业务语义增强
5. 错误码增强
6. 导出层（OpenAPI、Markdown、RAG）
7. CLI 入口
