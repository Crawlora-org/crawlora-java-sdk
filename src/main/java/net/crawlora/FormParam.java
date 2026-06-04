package net.crawlora;

import java.util.List;

/**
 * Metadata for one multipart form parameter of an operation. The current
 * contract has no form parameters, but the type and code path are kept for
 * forward compatibility.
 */
public final class FormParam {
    public final String name;
    public final boolean required;
    public final String type;
    public final List<String> enumValues;

    public FormParam(String name, boolean required, String type, List<String> enumValues) {
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
