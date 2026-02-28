package io.github.code2spec.parser;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import io.github.code2spec.core.model.*;
import io.github.code2spec.llm.EndpointContext;
import io.github.code2spec.llm.ErrorCodeContext;
import io.github.code2spec.llm.LlmEnhancer;

import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Parses Java source to extract REST endpoints and error codes.
 */
public class JavaRestParser {
    private final LlmEnhancer llmEnhancer;

    public JavaRestParser(LlmEnhancer llmEnhancer) {
        this.llmEnhancer = llmEnhancer;
    }

    public SpecResult parse(Path sourceRoot) throws Exception {
        SpecResult result = new SpecResult();
        List<Path> javaFiles = collectJavaFiles(sourceRoot);

        for (Path file : javaFiles) {
            ParseResult<CompilationUnit> parseResult = new JavaParser().parse(file);
            if (parseResult.isSuccessful() && parseResult.getResult().isPresent()) {
                CompilationUnit cu = parseResult.getResult().get();
                extractEndpoints(cu, result);
                extractErrorHandlers(cu, result);
            }
        }

        // Resolve error code references and enhance with LLM
        enhanceErrorCodes(result);

        return result;
    }

    private List<Path> collectJavaFiles(Path root) throws Exception {
        List<Path> files = new ArrayList<>();
        if (!root.toFile().exists()) return files;
        try (var stream = java.nio.file.Files.walk(root)) {
            stream.filter(p -> p.toString().endsWith(".java"))
                    .forEach(files::add);
        }
        return files;
    }

    private void extractEndpoints(CompilationUnit cu, SpecResult result) {
        cu.accept(new VoidVisitorAdapter<Void>() {
            @Override
            public void visit(ClassOrInterfaceDeclaration c, Void arg) {
                if (!isRestController(c)) return;
                String classPath = getClassRequestMapping(c);
                for (MethodDeclaration m : c.getMethods()) {
                    Endpoint ep = extractEndpoint(m, classPath);
                    if (ep != null) {
                        // Build context for LLM
                        EndpointContext ctx = buildEndpointContext(m, ep);
                        if (llmEnhancer != null && llmEnhancer.isEnabled()) {
                            BusinessSemantic semantic = llmEnhancer.enhanceEndpoint(ctx);
                            ep.setBusinessSemantic(semantic);
                        }
                        result.getEndpoints().add(ep);
                    }
                }
                super.visit(c, arg);
            }
        }, null);
    }

    private boolean isRestController(ClassOrInterfaceDeclaration c) {
        return c.getAnnotationByName("RestController").isPresent()
                || c.getAnnotationByName("Controller").isPresent()
                || hasAnnotation(c, "RestController")
                || hasAnnotation(c, "Controller");
    }

    private boolean hasAnnotation(ClassOrInterfaceDeclaration c, String name) {
        return c.getAnnotations().stream()
                .anyMatch(a -> a.getNameAsString().equals(name));
    }

    private String getClassRequestMapping(ClassOrInterfaceDeclaration c) {
        return c.getAnnotationByName("RequestMapping")
                .map(a -> {
                    if (a instanceof NormalAnnotationExpr n) {
                        return n.getPairs().stream()
                                .filter(p -> p.getNameAsString().equals("value") || p.getNameAsString().equals("path"))
                                .findFirst()
                                .map(p -> extractStringValue(p.getValue()))
                                .orElse("");
                    }
                    if (a instanceof SingleMemberAnnotationExpr s) {
                        return extractStringValue(s.getMemberValue());
                    }
                    return "";
                })
                .orElse("");
    }

    private Endpoint extractEndpoint(MethodDeclaration m, String classPath) {
        String httpMethod = null;
        String path = "";

        for (String name : List.of("GetMapping", "PostMapping", "PutMapping", "DeleteMapping", "PatchMapping", "RequestMapping")) {
            Optional<AnnotationExpr> ann = m.getAnnotationByName(name);
            if (ann.isPresent()) {
                if (name.equals("RequestMapping")) {
                    httpMethod = getRequestMappingMethod(ann.get());
                    path = getMappingPath(ann.get());
                } else {
                    httpMethod = name.replace("Mapping", "").toUpperCase();
                    path = getMappingPath(ann.get());
                }
                break;
            }
        }
        if (httpMethod == null) return null;

        String fullPath = normalizePath(classPath) + normalizePath(path);
        if (fullPath.isEmpty()) fullPath = "/";

        Endpoint ep = new Endpoint();
        ep.setUri(fullPath);
        ep.setHttpMethod(httpMethod);
        ep.setOperationId(m.getNameAsString());
        ep.setSummary(extractJavadocSummary(m));
        ep.setDescription(extractJavadocDescription(m));

        // Parameters
        m.getParameters().forEach(p -> {
            Parameter param = new Parameter();
            param.setName(p.getNameAsString());
            param.setType(p.getType().asString());
            if (p.getAnnotationByName("PathVariable").isPresent()) {
                param.setIn("path");
                param.setRequired(true);
            } else if (p.getAnnotationByName("RequestParam").isPresent()) {
                param.setIn("query");
                param.setRequired(p.getAnnotationByName("RequestParam")
                        .flatMap(a -> a.asNormalAnnotationExpr().getPairs().stream()
                                .filter(x -> x.getNameAsString().equals("required"))
                                .findFirst())
                        .map(pair -> !"false".equals(pair.getValue().toString()))
                        .orElse(false));
            } else if (p.getAnnotationByName("RequestBody").isPresent()) {
                ep.setRequestBodyType(p.getType().asString());
            }
            if (param.getIn() != null) ep.getParameters().add(param);
        });

        ep.setResponseType(m.getType().asString());
        return ep;
    }

    private String getRequestMappingMethod(AnnotationExpr ann) {
        if (ann instanceof NormalAnnotationExpr n) {
            return n.getPairs().stream()
                    .filter(p -> p.getNameAsString().equals("method"))
                    .findFirst()
                    .map(p -> p.getValue().toString().replace(".", "_").toUpperCase())
                    .orElse("GET");
        }
        return "GET";
    }

    private String getMappingPath(AnnotationExpr ann) {
        if (ann instanceof NormalAnnotationExpr n) {
            return n.getPairs().stream()
                    .filter(p -> p.getNameAsString().equals("value") || p.getNameAsString().equals("path"))
                    .findFirst()
                    .map(p -> extractStringValue(p.getValue()))
                    .orElse("");
        }
        if (ann instanceof SingleMemberAnnotationExpr s) {
            return extractStringValue(s.getMemberValue());
        }
        return "";
    }

    private String extractStringValue(Expression exp) {
        if (exp instanceof StringLiteralExpr s) return s.getValue();
        if (exp instanceof ArrayInitializerExpr arr && arr.getValues().size() > 0) {
            return extractStringValue(arr.getValues().get(0));
        }
        return "";
    }

    private String normalizePath(String p) {
        if (p == null || p.isBlank()) return "";
        p = p.trim();
        if (!p.startsWith("/")) p = "/" + p;
        if (p.length() > 1 && p.endsWith("/")) p = p.substring(0, p.length() - 1);
        return p;
    }

    private String extractJavadocSummary(MethodDeclaration m) {
        return m.getJavadoc()
                .map(j -> j.getDescription().toText().lines().findFirst().orElse("").trim())
                .orElse(m.getNameAsString());
    }

    private String extractJavadocDescription(MethodDeclaration m) {
        return m.getJavadoc()
                .map(j -> j.getDescription().toText().trim())
                .orElse(null);
    }

    private EndpointContext buildEndpointContext(MethodDeclaration m, Endpoint ep) {
        EndpointContext ctx = new EndpointContext();
        ctx.setUri(ep.getUri());
        ctx.setHttpMethod(ep.getHttpMethod());
        ctx.setMethodName(m.getNameAsString());
        ctx.setJavadoc(extractJavadocDescription(m));
        ctx.setParameterTypes(m.getParameters().stream().map(p -> p.getType().asString()).collect(Collectors.toList()));
        ctx.setReturnType(m.getType().asString());
        ctx.setMethodBodySnippet(m.getBody().map(b -> b.toString()).orElse(""));
        ctx.setCalledMethodNames(extractCalledMethods(m));
        return ctx;
    }

    private List<String> extractCalledMethods(MethodDeclaration m) {
        List<String> names = new ArrayList<>();
        m.getBody().ifPresent(body -> body.accept(new VoidVisitorAdapter<Void>() {
            @Override
            public void visit(MethodCallExpr n, Void arg) {
                names.add(n.getNameAsString());
                super.visit(n, arg);
            }
        }, null));
        return names.stream().distinct().limit(10).collect(Collectors.toList());
    }

    private final Map<String, String> errorHandlerSnippets = new HashMap<>();

    private void extractErrorHandlers(CompilationUnit cu, SpecResult result) {
        cu.accept(new VoidVisitorAdapter<Void>() {
            @Override
            public void visit(MethodDeclaration m, Void arg) {
                m.getAnnotationByName("ExceptionHandler").ifPresent(ann -> {
                    ErrorCode ec = extractErrorCodeFromHandler(m, ann);
                    if (ec != null && result.getErrorCodes().stream().noneMatch(e -> e.getCode().equals(ec.getCode()))) {
                        errorHandlerSnippets.put(ec.getCode(), m.getBody().map(b -> b.toString()).orElse(""));
                        result.getErrorCodes().add(ec);
                    }
                });
                super.visit(m, arg);
            }
        }, null);
    }

    private ErrorCode extractErrorCodeFromHandler(MethodDeclaration m, AnnotationExpr ann) {
        String exceptionType = "";
        if (ann instanceof NormalAnnotationExpr n) {
            exceptionType = n.getPairs().stream()
                    .filter(p -> p.getNameAsString().equals("value"))
                    .findFirst()
                    .map(p -> p.getValue().toString())
                    .orElse("");
        }
        if (exceptionType.isEmpty() && ann instanceof SingleMemberAnnotationExpr s) {
            exceptionType = s.getMemberValue().toString();
        }
        if (exceptionType.endsWith(".class")) {
            exceptionType = exceptionType.substring(0, exceptionType.length() - 6);
        } else if (exceptionType.contains(".")) {
            exceptionType = exceptionType.substring(exceptionType.lastIndexOf('.') + 1);
        }

        ErrorCode ec = new ErrorCode();
        ec.setCode(exceptionType);
        ec.setExceptionType(exceptionType);
        String msg = extractJavadocSummary(m);
        ec.setMessage((msg == null || msg.isBlank() || msg.startsWith("handle")) ? exceptionType : msg);
        ec.setHttpStatus(guessHttpStatus(m, exceptionType));
        return ec;
    }

    private int guessHttpStatus(MethodDeclaration m, String exceptionType) {
        String lower = exceptionType.toLowerCase();
        if (lower.contains("notfound") || lower.contains("not_found")) return 404;
        if (lower.contains("badrequest") || lower.contains("illegalargument")) return 400;
        if (lower.contains("unauthorized")) return 401;
        if (lower.contains("forbidden")) return 403;
        if (lower.contains("conflict") || lower.contains("insufficient") || lower.contains("stock")) return 409;
        return 500;
    }

    private void enhanceErrorCodes(SpecResult result) {
        for (ErrorCode ec : result.getErrorCodes()) {
            ErrorCodeContext ctx = new ErrorCodeContext();
            ctx.setCode(ec.getCode());
            ctx.setMessage(ec.getMessage());
            ctx.setHttpStatus(ec.getHttpStatus());
            ctx.setExceptionType(ec.getExceptionType());
            ctx.setExceptionHandlerSnippet(errorHandlerSnippets.getOrDefault(ec.getCode(), ""));
            if (llmEnhancer != null && llmEnhancer.isEnabled()) {
                llmEnhancer.enhanceErrorCode(ec, ctx);
            }
        }
    }
}
