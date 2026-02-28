# Code2Spec

Code2Spec 将 Java 代码仓库转换为结构化的 REST API 说明文档，支持 **LLM 增强** 的业务语义描述与错误码根因/处理建议。

## 功能特性

- **多源解析**：同时支持 Java 源码与 OpenAPI/Swagger YAML/JSON 文件，自动合并
- **规则提取**：基于 AST 解析 Java，提取 REST 端点、请求/响应 Schema、错误码映射
- **OpenAPI 文件**：解析 `openapi.yaml`、`swagger.yaml` 等标准定义，补充描述、约束、示例
- **LLM 增强**（可选）：
  - **业务语义描述**：功能概述、业务场景、实现要点、注意事项
  - **错误码增强**：根因描述、处理建议、预防建议
- **多格式输出**：OpenAPI 3.x、Markdown、RAG 知识对象（JSON）

## 快速开始

### 构建

```bash
mvn package -DskipTests
```

### 运行（仅规则提取，无需 API Key）

```bash
java -jar target/code2spec-jar-with-dependencies.jar <项目目录> -o ./output --no-llm
```

### 运行（启用 LLM 增强）

```bash
# 方式 1：环境变量
export OPENAI_API_KEY=sk-xxx
java -jar target/code2spec-jar-with-dependencies.jar <项目目录> -o ./output

# 方式 2：命令行参数
java -jar target/code2spec-jar-with-dependencies.jar <项目目录> -o ./output \
  --llm-api-key sk-xxx \
  --llm-api-base https://api.openai.com/v1 \
  --llm-model gpt-4o-mini
```

### 兼容其他 LLM 服务

支持任意 OpenAI 兼容 API，例如：

- **Azure OpenAI**：`--llm-api-base https://xxx.openai.azure.com/openai/deployments/xxx`
- **通义千问**：`--llm-api-base https://dashscope.aliyuncs.com/compatible-mode/v1`
- **DeepSeek**：`--llm-api-base https://api.deepseek.com/v1`
- **Ollama 本地**：`--llm-api-base http://localhost:11434/v1 --llm-model llama3`

## 输出说明

| 文件/目录 | 说明 |
|----------|------|
| `openapi.json` | OpenAPI 3.x 规范 |
| `api-docs.md` | 人类可读的 Markdown 文档 |
| `rag/` | RAG 知识对象，每个接口一个 JSON 文件，含业务语义与错误码说明 |

## 设计文档

详见 [docs/DESIGN.md](docs/DESIGN.md)，包含：

- 整体架构与数据流
- LLM 增强点设计（业务语义、错误码）
- Prompt 设计思路
- RAG 知识对象结构

## 示例项目

| 示例 | 说明 |
|------|------|
| `samples/demo-api/` | Spring MVC + JAX-RS + OpenAPI YAML |
| `samples/demo-jaxrs/` | 纯 JAX-RS + ServiceComb 风格 |

```bash
# 指定项目根目录（会同时扫描 Java 与 OpenAPI 文件）
java -jar target/code2spec-jar-with-dependencies.jar samples/demo-api -o ./output --no-llm
java -jar target/code2spec-jar-with-dependencies.jar samples/demo-jaxrs -o ./output --no-llm
```

## 配置说明

| 参数 | 说明 | 默认值 |
|-----|------|--------|
| `-o, --output` | 输出目录 | `./output` |
| `--llm-api-key` | LLM API Key | 环境变量 `OPENAI_API_KEY` |
| `--llm-api-base` | API 基础 URL | `https://api.openai.com/v1` |
| `--llm-model` | 模型名称 | `gpt-4o-mini` |
| `--no-llm` | 禁用 LLM 增强 | - |

## 支持的输入

### Java 注解

**Spring MVC**：
- **Controller**：`@RestController`、`@Controller`
- **路径**：`@RequestMapping`、`@GetMapping`、`@PostMapping`、`@PutMapping`、`@DeleteMapping`、`@PatchMapping`
- **参数**：`@PathVariable`、`@RequestParam`、`@RequestBody`

**JAX-RS / ServiceComb**：
- **资源**：`@Path`（类与方法级别）、`@RestSchema`（ServiceComb）
- **HTTP 方法**：`@GET`、`@POST`、`@PUT`、`@DELETE`、`@PATCH`、`@HEAD`、`@OPTIONS`
- **参数**：`@PathParam`、`@QueryParam`、`@HeaderParam`、`@FormParam`（无注解且非 Servlet 类型视为请求体）
- **兼容**：`javax.ws.rs` 与 `jakarta.ws.rs`（按注解简单名匹配）

**异常**：`@ExceptionHandler`（在 `@ControllerAdvice` / `@RestControllerAdvice` 中）

### OpenAPI/Swagger 文件

- **标准名称**：`openapi.yaml`、`openapi.yml`、`openapi.json`、`swagger.yaml`、`swagger.json`
- **命名模式**：`*openapi*.yaml`、`*swagger*.yaml`
- **规范版本**：OpenAPI 3.x、Swagger 2.0（自动转换）

指定项目根目录时，会递归扫描其中的 Java 与 YAML/JSON 文件并合并结果，OpenAPI 中的描述、参数、Schema 会补充或覆盖 Java 提取的内容。

## License

Code is the source of truth. Code2Spec turns it into structured knowledge.
