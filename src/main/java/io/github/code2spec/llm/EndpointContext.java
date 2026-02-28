package io.github.code2spec.llm;

import java.util.List;

/**
 * Context passed to LLM for business semantic enhancement.
 */
public class EndpointContext {
    private String uri;
    private String httpMethod;
    private String methodName;
    private String javadoc;
    private List<String> parameterTypes;
    private String returnType;
    private String methodBodySnippet;
    private List<String> calledMethodNames;
    private String callChainSnippet;

    public String getUri() { return uri; }
    public void setUri(String uri) { this.uri = uri; }

    public String getHttpMethod() { return httpMethod; }
    public void setHttpMethod(String httpMethod) { this.httpMethod = httpMethod; }

    public String getMethodName() { return methodName; }
    public void setMethodName(String methodName) { this.methodName = methodName; }

    public String getJavadoc() { return javadoc; }
    public void setJavadoc(String javadoc) { this.javadoc = javadoc; }

    public List<String> getParameterTypes() { return parameterTypes; }
    public void setParameterTypes(List<String> parameterTypes) { this.parameterTypes = parameterTypes; }

    public String getReturnType() { return returnType; }
    public void setReturnType(String returnType) { this.returnType = returnType; }

    public String getMethodBodySnippet() { return methodBodySnippet; }
    public void setMethodBodySnippet(String methodBodySnippet) { this.methodBodySnippet = methodBodySnippet; }

    public List<String> getCalledMethodNames() { return calledMethodNames; }
    public void setCalledMethodNames(List<String> calledMethodNames) { this.calledMethodNames = calledMethodNames; }

    public String getCallChainSnippet() { return callChainSnippet; }
    public void setCallChainSnippet(String callChainSnippet) { this.callChainSnippet = callChainSnippet; }
}
