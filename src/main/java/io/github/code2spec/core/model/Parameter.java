package io.github.code2spec.core.model;

/**
 * Request parameter (path, query, header).
 */
public class Parameter {
    private String name;
    private String in;  // path, query, header
    private String type;
    private boolean required;
    private String description;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getIn() { return in; }
    public void setIn(String in) { this.in = in; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public boolean isRequired() { return required; }
    public void setRequired(boolean required) { this.required = required; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
