package io.github.code2spec.parser;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Collects the full call chain (A -> B -> C) for an endpoint method.
 * Uses a method index to resolve callees within project source.
 *
 * <p>Supported: project source classes (Service, Repository with @Autowired),
 * method calls inside lambdas (e.g. CompletableFuture.supplyAsync(() -> service.create())).
 *
 * <p>Not supported: classes from JARs (Spring Boot auto-configured beans, library classes)
 * - their source is not in the project, so we cannot trace into them.
 */
public class CallChainCollector {

    private static final Set<String> SKIP_PACKAGES = Set.of("java.", "javax.", "jakarta.", "org.springframework.", "org.junit.");
    private static final Set<String> RELEVANT_ANNOTATIONS = Set.of("Transactional", "Cacheable", "Async", "CacheEvict", "Scheduled");

    private final int maxDepth;
    private final int maxTotalChars;
    private final Map<String, List<MethodDecl>> methodIndex = new HashMap<>();
    private final Map<String, ClassContext> classIndex = new HashMap<>();
    private final Map<String, List<String>> interfaceImplementations = new HashMap<>();

    public CallChainCollector() {
        this(3, 12000);
    }

    public CallChainCollector(int maxDepth) {
        this(maxDepth, 12000);
    }

    public CallChainCollector(int maxDepth, int maxTotalChars) {
        this.maxDepth = Math.max(0, maxDepth);
        this.maxTotalChars = Math.max(1000, maxTotalChars);
    }

    /**
     * Index all methods from parsed compilation units.
     */
    public void indexCompilationUnits(Map<Path, CompilationUnit> pathToCu) {
        for (CompilationUnit cu : pathToCu.values()) {
            indexUnit(cu);
        }
    }

    private void indexUnit(CompilationUnit cu) {
        CompilationUnit unit = cu;
        cu.accept(new VoidVisitorAdapter<Void>() {
            @Override
            public void visit(ClassOrInterfaceDeclaration c, Void arg) {
                String pkg = unit.getPackageDeclaration().map(pd -> pd.getNameAsString()).orElse("");
                String className = pkg.isEmpty() ? c.getNameAsString() : pkg + "." + c.getNameAsString();
                classIndex.put(className, new ClassContext(c, unit));
                for (MethodDeclaration m : c.getMethods()) {
                    methodIndex.computeIfAbsent(key(className, m.getNameAsString()), k -> new ArrayList<>()).add(new MethodDecl(className, m));
                }
                for (var ext : c.getExtendedTypes()) {
                    String superName = toQualifiedName(ext.getNameAsString(), unit);
                    interfaceImplementations.computeIfAbsent(superName, k -> new ArrayList<>()).add(className);
                }
                for (var impl : c.getImplementedTypes()) {
                    String ifaceName = toQualifiedName(impl.getNameAsString(), unit);
                    interfaceImplementations.computeIfAbsent(ifaceName, k -> new ArrayList<>()).add(className);
                }
                super.visit(c, arg);
            }
        }, null);
    }

    private static String key(String className, String methodName) {
        return className + "#" + methodName;
    }

    /**
     * Collect call chain code for the given endpoint method.
     */
    public String collectCallChain(MethodDeclaration endpointMethod, ClassOrInterfaceDeclaration containingClass, CompilationUnit cu) {
        StringBuilder out = new StringBuilder();
        Set<String> visited = new HashSet<>();
        int[] totalChars = new int[1];

        String className = getClassName(containingClass, cu);
        appendMethod(out, "接口方法 " + className + "." + endpointMethod.getNameAsString(), endpointMethod, 0, visited, totalChars);

        return out.toString().trim();
    }

    private String getClassName(ClassOrInterfaceDeclaration c, CompilationUnit cu) {
        String pkg = cu.getPackageDeclaration().map(pd -> pd.getNameAsString()).orElse("");
        return pkg.isEmpty() ? c.getNameAsString() : pkg + "." + c.getNameAsString();
    }

    private void appendMethod(StringBuilder out, String label, MethodDeclaration m, int depth, Set<String> visited, int[] totalChars) {
        if (depth > maxDepth || totalChars[0] > maxTotalChars) return;

        StringBuilder entry = new StringBuilder();
        entry.append(label).append(":\n");
        String javadoc = m.getJavadoc().map(j -> j.getDescription().toText().trim()).orElse(null);
        if (javadoc != null && !javadoc.isBlank()) {
            String jd = javadoc.replace("\n", " ");
            entry.append("  /** ").append(jd.substring(0, Math.min(jd.length(), 300))).append(jd.length() > 300 ? "..." : "").append(" */\n");
        }
        var anns = m.getAnnotations().stream()
                .filter(a -> RELEVANT_ANNOTATIONS.contains(a.getNameAsString()))
                .map(a -> "@" + a.getNameAsString())
                .toList();
        if (!anns.isEmpty()) {
            entry.append("  ").append(String.join(" ", anns)).append("\n");
        }
        String sig = m.getDeclarationAsString(false, false, false);
        String body = m.getBody().map(b -> b.toString()).orElse("{}");
        entry.append("  ").append(sig).append(" {\n").append(body).append("\n  }\n\n");
        out.append(entry);
        totalChars[0] += entry.length();

        if (depth >= maxDepth) return;

        List<MethodCallInfo> calls = extractMethodCalls(m, m.findAncestor(ClassOrInterfaceDeclaration.class).orElse(null), m.findAncestor(CompilationUnit.class).orElse(null));
        for (MethodCallInfo call : calls) {
            MethodDeclaration callee = resolveCallee(call);
            if (callee != null) {
                String visitKey = callee.getRange().map(r -> r.begin.toString()).orElse("") + callee.getNameAsString();
                if (visited.add(visitKey)) {
                    String callLabel = "  -> " + call.resolvedClassName + "." + callee.getNameAsString();
                    if (call.condition != null && !call.condition.isBlank()) {
                        callLabel += " (条件: " + truncateStr(call.condition, 80) + ")";
                    }
                    appendMethod(out, callLabel, callee, depth + 1, visited, totalChars);
                }
            }
        }
    }

    private static String truncateStr(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }

    private List<MethodCallInfo> extractMethodCalls(MethodDeclaration m, ClassOrInterfaceDeclaration containingClass, CompilationUnit cu) {
        List<MethodCallInfo> calls = new ArrayList<>();
        if (containingClass == null || cu == null) return calls;

        m.getBody().ifPresent(body -> body.accept(new VoidVisitorAdapter<Void>() {
            @Override
            public void visit(MethodCallExpr n, Void arg) {
                collectCallFromExpr(n, containingClass, m, cu, calls);
                super.visit(n, arg);
            }

            @Override
            public void visit(com.github.javaparser.ast.expr.LambdaExpr n, Void arg) {
                n.getBody().accept(this, arg);
                super.visit(n, arg);
            }

            @Override
            public void visit(MethodReferenceExpr n, Void arg) {
                collectCallFromMethodRef(n, containingClass, m, cu, calls);
                super.visit(n, arg);
            }
        }, null));

        return calls.stream().collect(Collectors.toMap(c -> c.resolvedClassName + "#" + c.methodName, c -> c, (a, b) -> a)).values().stream().toList();
    }

    private void collectCallFromExpr(MethodCallExpr n, ClassOrInterfaceDeclaration containingClass, MethodDeclaration method, CompilationUnit cu, List<MethodCallInfo> calls) {
        Expression scope = n.getScope().orElse(null);
        if (scope == null) scope = new NameExpr("this");
        String scopeType = resolveScopeType(scope, containingClass, method, cu);
        if (scopeType != null && !shouldSkip(scopeType)) {
            String condition = findEnclosingCondition(n);
            calls.add(new MethodCallInfo(n.getNameAsString(), n.getArguments().size(), scopeType, condition));
        }
    }

    private void collectCallFromMethodRef(MethodReferenceExpr n, ClassOrInterfaceDeclaration containingClass, MethodDeclaration method, CompilationUnit cu, List<MethodCallInfo> calls) {
        Expression scope = n.getScope();
        String methodName = n.getIdentifier();
        if ("new".equals(methodName)) return;
        String scopeType = resolveScopeTypeForMethodRef(scope, containingClass, method, cu);
        if (scopeType != null && !shouldSkip(scopeType)) {
            calls.add(new MethodCallInfo(methodName, -1, scopeType, null));
        }
    }

    private String resolveScopeTypeForMethodRef(Expression scope, ClassOrInterfaceDeclaration containingClass, MethodDeclaration method, CompilationUnit cu) {
        if (scope instanceof com.github.javaparser.ast.expr.ThisExpr) {
            return getClassName(containingClass, cu);
        }
        if (scope instanceof NameExpr ne) {
            String name = ne.getNameAsString();
            String fieldType = findFieldType(containingClass, name, cu);
            if (fieldType != null) return fieldType;
            String paramType = method.getParameterByName(name).map(p -> p.getType().asString()).orElse(null);
            if (paramType != null) return toQualifiedName(paramType, cu);
            return resolveClassByName(name, cu);
        }
        if (scope instanceof FieldAccessExpr fa) {
            Expression scopeExpr = fa.getScope();
            String scopeType = resolveScopeTypeForMethodRef(scopeExpr, containingClass, method, cu);
            if (scopeType != null) {
                String fieldType = findFieldTypeInClass(scopeType, fa.getNameAsString());
                return fieldType != null ? fieldType : scopeType;
            }
        }
        return null;
    }

    private String resolveClassByName(String simpleName, CompilationUnit cu) {
        String pkg = cu.getPackageDeclaration().map(pd -> pd.getNameAsString()).orElse("");
        String fullName = pkg.isEmpty() ? simpleName : pkg + "." + simpleName;
        if (classIndex.containsKey(fullName)) return fullName;
        return classIndex.keySet().stream()
                .filter(k -> k.endsWith("." + simpleName) || k.equals(simpleName))
                .findFirst()
                .orElse(null);
    }

    private String findEnclosingCondition(MethodCallExpr n) {
        return n.findAncestor(com.github.javaparser.ast.stmt.IfStmt.class)
                .map(ifStmt -> ifStmt.getCondition().toString())
                .orElse(n.findAncestor(com.github.javaparser.ast.stmt.ForStmt.class)
                        .flatMap(f -> f.getCompare())
                        .map(c -> c.toString())
                        .orElse(null));
    }

    private String resolveScopeType(Expression scope, ClassOrInterfaceDeclaration containingClass, MethodDeclaration method, CompilationUnit cu) {
        if (scope instanceof NameExpr ne) {
            String name = ne.getNameAsString();
            if ("this".equals(name)) {
                return getClassName(containingClass, cu);
            }
            String fieldType = findFieldType(containingClass, name, cu);
            if (fieldType != null) return fieldType;
            String paramType = method.getParameterByName(name).map(p -> p.getType().asString()).orElse(null);
            if (paramType != null) return toQualifiedName(paramType, cu);
            return resolveClassByName(name, cu);
        }
        if (scope instanceof FieldAccessExpr fa) {
            Expression scopeExpr = fa.getScope();
            String scopeType = resolveScopeType(scopeExpr, containingClass, method, cu);
            if (scopeType != null) {
                String fieldType = findFieldTypeInClass(scopeType, fa.getNameAsString());
                return fieldType != null ? fieldType : scopeType + "$" + fa.getNameAsString();
            }
        }
        if (scope instanceof MethodCallExpr mce) {
            Expression mceScope = mce.getScope().orElse(null);
            if (mceScope == null) return null;
            String scopeType = resolveScopeType(mceScope, containingClass, method, cu);
            if (scopeType != null) {
                MethodDeclaration resolved = findMethod(scopeType, mce.getNameAsString(), mce.getArguments().size());
                if (resolved != null) {
                    String retType = resolved.getType().asString().replaceAll("<[^>]+>", "").trim();
                    return toQualifiedName(retType, resolved.findAncestor(CompilationUnit.class).orElse(null));
                }
            }
        }
        return null;
    }

    private String findFieldType(ClassOrInterfaceDeclaration c, String fieldName, CompilationUnit cu) {
        for (var f : c.getFields()) {
            for (var v : f.getVariables()) {
                if (v.getNameAsString().equals(fieldName)) {
                    return toQualifiedName(v.getTypeAsString(), cu);
                }
            }
        }
        return null;
    }

    private String findFieldTypeInClass(String className, String fieldName) {
        ClassContext cc = classIndex.get(className);
        if (cc != null) {
            return findFieldType(cc.cls, fieldName, cc.cu);
        }
        return null;
    }

    private String toQualifiedName(String type, CompilationUnit cu) {
        if (type == null) return null;
        type = type.replaceAll("<.*>", "").trim();
        if (type.contains(".")) return type;
        String pkg = cu != null ? cu.getPackageDeclaration().map(pd -> pd.getNameAsString()).orElse("") : "";
        return pkg.isEmpty() ? type : pkg + "." + type;
    }

    private boolean shouldSkip(String type) {
        return SKIP_PACKAGES.stream().anyMatch(type::startsWith);
    }

    private MethodDeclaration resolveCallee(MethodCallInfo call) {
        List<MethodDecl> candidates = methodIndex.get(key(call.resolvedClassName, call.methodName));
        if (candidates == null) {
            List<String> typesToTry = new ArrayList<>();
            typesToTry.add(call.resolvedClassName);
            List<String> impls = interfaceImplementations.get(call.resolvedClassName);
            if (impls != null) typesToTry.addAll(impls);
            for (String type : typesToTry) {
                candidates = methodIndex.get(key(type, call.methodName));
                if (candidates != null && !candidates.isEmpty()) break;
            }
        }
        if (candidates == null || candidates.isEmpty()) {
            int dot = call.resolvedClassName.lastIndexOf('.');
            if (dot > 0) {
                String simple = call.resolvedClassName.substring(dot + 1);
                candidates = methodIndex.entrySet().stream()
                        .filter(e -> e.getKey().endsWith("#" + call.methodName))
                        .flatMap(e -> e.getValue().stream())
                        .filter(md -> md.className.endsWith("." + simple) || md.className.equals(simple))
                        .collect(Collectors.toList());
            }
        }
        if (candidates == null || candidates.isEmpty()) return null;
        if (call.argCount >= 0) {
            return candidates.stream()
                    .filter(md -> md.m.getParameters().size() == call.argCount)
                    .findFirst()
                    .map(md -> md.m)
                    .orElse(candidates.get(0).m);
        }
        return candidates.get(0).m;
    }

    private MethodDeclaration findMethod(String className, String methodName, int argCount) {
        List<MethodDecl> candidates = methodIndex.get(key(className, methodName));
        if (candidates == null) return null;
        return candidates.stream()
                .filter(md -> md.m.getParameters().size() == argCount)
                .findFirst()
                .map(md -> md.m)
                .orElse(candidates.isEmpty() ? null : candidates.get(0).m);
    }

    private record MethodCallInfo(String methodName, int argCount, String resolvedClassName, String condition) {
        MethodCallInfo(String methodName, int argCount, String resolvedClassName) {
            this(methodName, argCount, resolvedClassName, null);
        }
    }

    private record MethodDecl(String className, MethodDeclaration m) {}

    private record ClassContext(ClassOrInterfaceDeclaration cls, CompilationUnit cu) {}
}
