# Code2Spec 示例项目

本目录包含用于验证 code2spec 解析能力的示例项目。

## 示例列表

| 示例 | 技术栈 | 验证点 |
|------|--------|--------|
| **demo-api** | Spring MVC + JAX-RS + OpenAPI YAML | @RestController、@RequestMapping、@GetMapping 等；@Path、@GET/@POST；OpenAPI 文件解析与合并；@ExceptionHandler |
| **demo-jaxrs** | JAX-RS + ServiceComb | @RestSchema、@Path；@HeaderParam、@PathParam、@QueryParam；@ExceptionHandler({X.class}) 数组形式 |

## 运行验证

```bash
# 构建
mvn package -DskipTests

# 验证 demo-api（Spring MVC + JAX-RS + OpenAPI）
java -jar target/code2spec-jar-with-dependencies.jar samples/demo-api -o ./output --no-llm

# 验证 demo-jaxrs（纯 JAX-RS + ServiceComb）
java -jar target/code2spec-jar-with-dependencies.jar samples/demo-jaxrs -o ./output --no-llm
```

## 单元测试覆盖

- `JavaRestParserTest.parseSpringMvcController`：Spring MVC 端点解析
- `JavaRestParserTest.parseJaxRsResource`：JAX-RS 端点解析（demo-api）
- `JavaRestParserTest.parseDemoJaxRsServiceCombStyle`：ServiceComb + JAX-RS（demo-jaxrs）
- `OpenApiFileParserTest.parseOpenApiYaml`：OpenAPI YAML 解析
- `PipelineTest.runPipelineOnDemoApi`：完整 Pipeline（Java + OpenAPI 合并）
- `PipelineTest.runPipelineOnDemoJaxrs`：完整 Pipeline（demo-jaxrs）
