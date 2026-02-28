package io.github.code2spec.parser;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import io.github.code2spec.ProgressReporter;
import io.github.code2spec.core.model.*;
import io.github.code2spec.llm.EndpointContext;
import io.github.code2spec.llm.ErrorCodeContext;
import io.github.code2spec.llm.LlmEnhancer;

import java.nio.file.Path;
import java.util.*;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Parses Java source to extract REST endpoints and error codes.
 */
public class JavaRestParser {
    private final LlmEnhancer llmEnhancer;
    private final ProgressReporter progressReporter;
    private final int callChainDepth;

    public JavaRestParser(LlmEnhancer llmEnhancer) {
        this(llmEnhancer, null, 3);
    }

    public JavaRestParser(LlmEnhancer llmEnhancer, ProgressReporter progressReporter) {
        this(llmEnhancer, progressReporter, 3);
    }

    public JavaRestParser(LlmEnhancer llmEnhancer, ProgressReporter progressReporter, int callChainDepth) {
        this.llmEnhancer = llmEnhancer;
        this.progressReporter = progressReporter;
        this.callChainDepth = callChainDepth;
    }

    public SpecResult parse(Path sourceRoot) throws Exception {
        SpecResult result = new SpecResult();
        List<Path> javaFiles = collectJavaFiles(sourceRoot);

        if (progressReporter != null) {
            progressReporter.onParseJavaStart(javaFiles.size());
        }

        Map<Path, CompilationUnit> pathToCu = new LinkedHashMap<>();
        JavaParser parser = new JavaParser();
        for (int i = 0; i < javaFiles.size(); i++) {
            Path file = javaFiles.get(i);
            if (progressReporter != null) {
                progressReporter.onParseJavaFile(i + 1, javaFiles.size(), file.toString());
            }
            ParseResult<CompilationUnit> parseResult = parser.parse(file);
            if (parseResult.isSuccessful() && parseResult.getResult().isPresent()) {
                pathToCu.put(file, parseResult.getResult().get());
            }
        }

        CallChainCollector callChainCollector = new CallChainCollector(callChainDepth);
        callChainCollector.indexCompilationUnits(pathToCu);

        int endpointCount = 0;
        for (CompilationUnit cu : pathToCu.values()) {
            endpointCount = extractEndpoints(cu, result, endpointCount, callChainCollector);
            extractErrorHandlers(cu, result);
        }

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

    private int extractEndpoints(CompilationUnit cu, SpecResult result, int endpointCount, CallChainCollector callChainCollector) {
        int[] count = new int[] { endpointCount };
        cu.accept(new VoidVisitorAdapter<Void>() {
            @Override
            public void visit(ClassOrInterfaceDeclaration c, Void arg) {
                if (!isRestResource(c)) return;
                String classPath = getClassPath(c);
                for (MethodDeclaration m : c.getMethods()) {
                    Endpoint ep = extractSpringEndpoint(m, classPath);
                    if (ep == null) ep = extractJaxRsEndpoint(m, classPath);
                    if (ep != null) {
                        EndpointContext ctx = buildEndpointContext(m, ep, c, cu, callChainCollector);
                        if (llmEnhancer != null && llmEnhancer.isEnabled()) {
                            if (progressReporter != null) {
                                progressReporter.onLlmEndpointStart(++count[0], 0, ep.getHttpMethod() + " " + ep.getUri());
                            }
                            BusinessSemantic semantic = llmEnhancer.enhanceEndpoint(ctx);
                            ep.setBusinessSemantic(semantic);
                        }
                        result.getEndpoints().add(ep);
                    }
                }
                super.visit(c, arg);
            }
        }, null);
        return count[0];
    }

    private boolean isRestResource(ClassOrInterfaceDeclaration c) {
        return c.getAnnotationByName("RestController").isPresent()
                || c.getAnnotationByName("Controller").isPresent()
                || c.getAnnotationByName("Path").isPresent()
                || c.getAnnotationByName("RestSchema").isPresent()
                || hasAnnotation(c, "RestController")
                || hasAnnotation(c, "Controller")
                || hasAnnotation(c, "Path")
                || hasAnnotation(c, "RestSchema");
    }

    private boolean hasAnnotation(ClassOrInterfaceDeclaration c, String name) {
        return c.getAnnotations().stream()
                .anyMatch(a -> a.getNameAsString().equals(name));
    }

    private String getClassPath(ClassOrInterfaceDeclaration c) {
        String path = c.getAnnotationByName("RequestMapping")
                .map(this::getMappingPath)
                .orElse("");
        if (path.isEmpty()) {
            path = c.getAnnotationByName("Path")
                    .map(this::getPathValue)
                    .orElse("");
        }
        return path;
    }

    private String getPathValue(AnnotationExpr ann) {
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

    private Endpoint extractSpringEndpoint(MethodDeclaration m, String classPath) {
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

        return buildEndpoint(m, classPath, path, httpMethod, true);
    }

    private Endpoint extractJaxRsEndpoint(MethodDeclaration m, String classPath) {
        String httpMethod = null;
        for (String name : List.of("GET", "POST", "PUT", "DELETE", "PATCH", "HEAD", "OPTIONS")) {
            if (m.getAnnotationByName(name).isPresent()) {
                httpMethod = name;
                break;
            }
        }
        if (httpMethod == null) return null;

        String path = m.getAnnotationByName("Path").map(this::getPathValue).orElse("");

        return buildEndpoint(m, classPath, path, httpMethod, false);
    }

    private Endpoint buildEndpoint(MethodDeclaration m, String classPath, String methodPath, String httpMethod, boolean isSpring) {
        String fullPath = normalizePath(classPath) + normalizePath(methodPath);
        if (fullPath.isEmpty()) fullPath = "/";

        Endpoint ep = new Endpoint();
        ep.setUri(fullPath);
        ep.setHttpMethod(httpMethod);
        ep.setOperationId(m.getNameAsString());
        ep.setSummary(extractJavadocSummary(m));
        ep.setDescription(extractJavadocDescription(m));

        m.getParameters().forEach(p -> {
            Parameter param = new Parameter();
            param.setName(p.getNameAsString());
            param.setType(p.getType().asString());
            if (isSpring) {
                extractSpringParams(p, ep, param);
            } else {
                extractJaxRsParams(p, ep, param);
            }
            if (param.getIn() != null) ep.getParameters().add(param);
        });

        ep.setResponseType(m.getType().asString());
        return ep;
    }

    private void extractSpringParams(com.github.javaparser.ast.body.Parameter p, Endpoint ep, Parameter param) {
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
    }

    private static final Set<String> SERVLET_TYPES = Set.of(
            "HttpServletRequest", "HttpServletResponse", "ServletRequest", "ServletResponse",
            "javax.servlet.http.HttpServletRequest", "javax.servlet.http.HttpServletResponse",
            "jakarta.servlet.http.HttpServletRequest", "jakarta.servlet.http.HttpServletResponse");

    private void extractJaxRsParams(com.github.javaparser.ast.body.Parameter p, Endpoint ep, Parameter param) {
        if (p.getAnnotationByName("PathParam").isPresent()) {
            param.setIn("path");
            param.setRequired(true);
            param.setName(extractJaxRsParamName(p, "PathParam"));
        } else if (p.getAnnotationByName("QueryParam").isPresent()) {
            param.setIn("query");
            param.setRequired(false);
            param.setName(extractJaxRsParamName(p, "QueryParam"));
        } else if (p.getAnnotationByName("HeaderParam").isPresent()) {
            param.setIn("header");
            param.setRequired(false);
            param.setName(extractJaxRsParamName(p, "HeaderParam"));
        } else if (p.getAnnotationByName("FormParam").isPresent()) {
            param.setIn("formData");
            param.setRequired(false);
            param.setName(extractJaxRsParamName(p, "FormParam"));
        } else if (p.getAnnotationByName("BeanParam").isPresent()) {
            // BeanParam typically contains multiple params, skip for now
        } else if (!isServletOrContextType(p.getType().asString())) {
            // No JAX-RS param annotation and not Servlet type = request body
            ep.setRequestBodyType(p.getType().asString());
        }
    }

    private boolean isServletOrContextType(String typeName) {
        if (typeName == null || typeName.isBlank()) return false;
        String simple = typeName.contains(".") ? typeName.substring(typeName.lastIndexOf('.') + 1) : typeName;
        return SERVLET_TYPES.contains(typeName) || SERVLET_TYPES.contains(simple);
    }

    private String extractJaxRsParamName(com.github.javaparser.ast.body.Parameter p, String annName) {
        Optional<AnnotationExpr> ann = p.getAnnotationByName(annName);
        if (ann.isEmpty()) return p.getNameAsString();
        AnnotationExpr a = ann.get();
        if (a instanceof NormalAnnotationExpr n) {
            return n.getPairs().stream()
                    .filter(x -> x.getNameAsString().equals("value"))
                    .findFirst()
                    .map(x -> extractStringValue(x.getValue()))
                    .orElse(p.getNameAsString());
        }
        if (a instanceof SingleMemberAnnotationExpr s) {
            return extractStringValue(s.getMemberValue());
        }
        return p.getNameAsString();
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
        return buildEndpointContext(m, ep, null, null, null);
    }

    private EndpointContext buildEndpointContext(MethodDeclaration m, Endpoint ep, ClassOrInterfaceDeclaration containingClass, CompilationUnit cu, CallChainCollector callChainCollector) {
        EndpointContext ctx = new EndpointContext();
        ctx.setUri(ep.getUri());
        ctx.setHttpMethod(ep.getHttpMethod());
        ctx.setMethodName(m.getNameAsString());
        ctx.setJavadoc(extractJavadocDescription(m));
        ctx.setParameterTypes(m.getParameters().stream().map(p -> p.getType().asString()).collect(Collectors.toList()));
        ctx.setReturnType(m.getType().asString());
        ctx.setMethodBodySnippet(m.getBody().map(b -> b.toString()).orElse(""));
        ctx.setCalledMethodNames(extractCalledMethods(m));
        if (callChainCollector != null && containingClass != null && cu != null) {
            try {
                String callChain = callChainCollector.collectCallChain(m, containingClass, cu);
                if (callChain != null && !callChain.isBlank()) {
                    ctx.setCallChainSnippet(callChain);
                }
            } catch (Exception ignored) {
                // Fallback to method body only when call chain collection fails
            }
        }
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
                    List<String> exceptionTypes = extractExceptionTypesFromHandler(ann);
                    String handlerSnippet = m.getBody().map(b -> b.toString()).orElse("");
                    for (String exceptionType : exceptionTypes) {
                        if (isValidExceptionType(exceptionType)
                                && result.getErrorCodes().stream().noneMatch(e -> e.getCode().equals(exceptionType))) {
                            ErrorCode ec = buildErrorCode(m, exceptionType);
                            errorHandlerSnippets.put(exceptionType, handlerSnippet);
                            result.getErrorCodes().add(ec);
                        }
                    }
                });
                super.visit(m, arg);
            }
        }, null);
    }

    private List<String> extractExceptionTypesFromHandler(AnnotationExpr ann) {
        List<String> types = new ArrayList<>();
        Expression valueExpr = null;
        if (ann instanceof NormalAnnotationExpr n) {
            valueExpr = n.getPairs().stream()
                    .filter(p -> p.getNameAsString().equals("value"))
                    .findFirst()
                    .map(p -> p.getValue())
                    .orElse(null);
        } else if (ann instanceof SingleMemberAnnotationExpr s) {
            valueExpr = s.getMemberValue();
        }
        if (valueExpr != null) {
            collectExceptionTypes(valueExpr, types);
        }
        return types;
    }

    private void collectExceptionTypes(Expression exp, List<String> types) {
        if (exp instanceof ArrayInitializerExpr arr) {
            for (Expression e : arr.getValues()) {
                collectExceptionTypes(e, types);
            }
        } else {
            String name = extractTypeNameFromClassExpr(exp);
            if (name != null && !name.isBlank()) types.add(name);
        }
    }

    private String extractTypeNameFromClassExpr(Expression exp) {
        if (exp instanceof ClassExpr ce) {
            String full = ce.getType().asString();
            return full.contains(".") ? full.substring(full.lastIndexOf('.') + 1) : full;
        }
        if (exp instanceof FieldAccessExpr fa && "class".equals(fa.getNameAsString())) {
            Expression scope = fa.getScope();
            if (scope instanceof com.github.javaparser.ast.expr.NameExpr ne) {
                return ne.getNameAsString();
            }
            if (scope instanceof FieldAccessExpr) {
                return scope.toString();
            }
            return scope.toString().replace(".class", "").replaceAll(".*\\.", "");
        }
        if (exp instanceof com.github.javaparser.ast.expr.NameExpr ne) {
            return ne.getNameAsString();
        }
        String s = exp.toString();
        if (s.endsWith(".class")) return s.substring(0, s.length() - 6).replaceAll(".*\\.", "");
        if (s.matches(".*[A-Za-z][A-Za-z0-9_]*")) return s.replaceAll(".*\\.([A-Za-z][A-Za-z0-9_]*)", "$1");
        return null;
    }

    private boolean isValidExceptionType(String s) {
        if (s == null || s.isBlank()) return false;
        if (s.contains("}") || s.contains("{") || "class".equals(s)) return false;
        return s.matches("[A-Za-z][A-Za-z0-9_]*");
    }

    private ErrorCode buildErrorCode(MethodDeclaration m, String exceptionType) {
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
        var errorCodes = result.getErrorCodes();
        int total = errorCodes.size();
        for (int i = 0; i < total; i++) {
            ErrorCode ec = errorCodes.get(i);
            ErrorCodeContext ctx = new ErrorCodeContext();
            ctx.setCode(ec.getCode());
            ctx.setMessage(ec.getMessage());
            ctx.setHttpStatus(ec.getHttpStatus());
            ctx.setExceptionType(ec.getExceptionType());
            ctx.setExceptionHandlerSnippet(errorHandlerSnippets.getOrDefault(ec.getCode(), ""));
            if (llmEnhancer != null && llmEnhancer.isEnabled()) {
                if (progressReporter != null) {
                    progressReporter.onLlmErrorCodeStart(i + 1, total, ec.getCode());
                }
                llmEnhancer.enhanceErrorCode(ec, ctx);
            }
        }
    }
}
