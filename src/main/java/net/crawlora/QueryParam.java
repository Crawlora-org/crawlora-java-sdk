package net.crawlora;

import java.util.List;

/**
 * Metadata for one query parameter of an operation: its wire name, whether it
 * is required, its OpenAPI primitive type, and any enum constraint.
 */
public final class QueryParam {
    public final String name;
    public final boolean required;
    public final String type;
    public final List<String> enumValues;

    public QueryParam(String name, boolean required, String type, List<String> enumValues) {
        this.name = name;
        this.required = required;
        this.type = type;
        this.enumValues = enumValues;
    }

    public String getName() {
        return name;
    }

    public boolean isRequired() {
        return required;
    }

    public List<String> getEnumValues() {
        return enumValues;
    }
}
