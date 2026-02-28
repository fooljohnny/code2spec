package io.github.code2spec.core.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Request or response body schema with field definitions.
 */
public class SchemaDefinition {
    private String schemaName;
    private List<SchemaField> fields = new ArrayList<>();

    public SchemaDefinition() {}
    public SchemaDefinition(String schemaName) {
        this.schemaName = schemaName;
    }

    public String getSchemaName() { return schemaName; }
    public void setSchemaName(String schemaName) { this.schemaName = schemaName; }

    public List<SchemaField> getFields() { return fields; }
    public void setFields(List<SchemaField> fields) { this.fields = fields; }
}
