package io.github.code2spec.core.model;

/**
 * LLM-enhanced business semantic description for an endpoint.
 */
public class BusinessSemantic {
    private String function;           // 功能概述
    private String scenario;           // 业务场景
    private String implementationNotes; // 实现要点
    private String cautions;           // 注意事项

    public String getFunction() { return function; }
    public void setFunction(String function) { this.function = function; }

    public String getScenario() { return scenario; }
    public void setScenario(String scenario) { this.scenario = scenario; }

    public String getImplementationNotes() { return implementationNotes; }
    public void setImplementationNotes(String implementationNotes) { this.implementationNotes = implementationNotes; }

    public String getCautions() { return cautions; }
    public void setCautions(String cautions) { this.cautions = cautions; }
}
