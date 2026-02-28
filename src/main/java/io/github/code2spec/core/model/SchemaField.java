package io.github.code2spec.core.model;

/**
 * Schema field for request/response body with constraints.
 */
public class SchemaField {
    private String name;
    private String type;
    private boolean required;
    private String description;
    private Integer minimum;
    private Integer maximum;
    private Integer minLength;
    private Integer maxLength;
    private String pattern;
    private String format;
    private String example;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public boolean isRequired() { return required; }
    public void setRequired(boolean required) { this.required = required; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Integer getMinimum() { return minimum; }
    public void setMinimum(Integer minimum) { this.minimum = minimum; }

    public Integer getMaximum() { return maximum; }
    public void setMaximum(Integer maximum) { this.maximum = maximum; }

    public Integer getMinLength() { return minLength; }
    public void setMinLength(Integer minLength) { this.minLength = minLength; }

    public Integer getMaxLength() { return maxLength; }
    public void setMaxLength(Integer maxLength) { this.maxLength = maxLength; }

    public String getPattern() { return pattern; }
    public void setPattern(String pattern) { this.pattern = pattern; }

    public String getFormat() { return format; }
    public void setFormat(String format) { this.format = format; }

    public String getExample() { return example; }
    public void setExample(String example) { this.example = example; }
}
