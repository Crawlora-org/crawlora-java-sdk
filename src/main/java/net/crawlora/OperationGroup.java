package net.crawlora;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Dynamic accessor for an operation group, used by {@link CrawloraClient#groupOf}.
 * Dispatches {@code group.call("search", params)} to the underlying operation id,
 * validating that supplied params are accepted by the operation. The generated
 * per-group classes (e.g. {@code BingGroup}) offer the same behaviour with one
 * named method per operation.
 */
public final class OperationGroup {
    private final CrawloraClient client;
    private final Map<String, String> operations;

    public OperationGroup(CrawloraClient client, Map<String, String> operations) {
        this.client = client;
        this.operations = operations;
    }

    /** Method names available on this group. */
    public Set<String> methods() {
        return operations.keySet();
    }

    /** Invoke a method by its generated name. */
    public Object call(String method, Map<String, Object> params) {
        return call(method, params, null);
    }

    /** Invoke a method by its generated name with explicit options. */
    public Object call(String method, Map<String, Object> params, RequestOptions options) {
        String operationId = operations.get(method);
        if (operationId == null) {
            throw new IllegalArgumentException("unknown method for group: " + method);
        }
        Set<String> allowed = allowedParams(operationId);
        Set<String> unknown = new TreeSet<>();
        if (params != null) {
            for (String key : params.keySet()) {
                if (!allowed.contains(key)) {
                    unknown.add(key);
                }
            }
        }
        if (!unknown.isEmpty()) {
            throw new IllegalArgumentException(
                    "unexpected parameter(s) for " + operationId + ": " + String.join(", ", unknown));
        }
        return client.request(operationId, params, options);
    }

    /**
     * Validates that every supplied parameter is accepted by the operation,
     * throwing {@link IllegalArgumentException} otherwise. Used by the generated
     * typed group methods.
     */
    public static void checkParams(String operationId, Map<String, Object> params) {
        Set<String> allowed = allowedParams(operationId);
        Set<String> unknown = new TreeSet<>();
        if (params != null) {
            for (String key : params.keySet()) {
                if (!allowed.contains(key)) {
                    unknown.add(key);
                }
            }
        }
        if (!unknown.isEmpty()) {
            throw new IllegalArgumentException(
                    "unexpected parameter(s) for " + operationId + ": " + String.join(", ", unknown));
        }
    }

    static Set<String> allowedParams(String operationId) {
        Operation operation = Operations.OPERATIONS.get(operationId);
        Set<String> allowed = new HashSet<>();
        if (operation == null) {
            return allowed;
        }
        allowed.addAll(operation.pathParams);
        for (QueryParam p : operation.queryParams) {
            allowed.add(p.name);
        }
        for (FormParam p : operation.formParams) {
            allowed.add(p.name);
        }
        if (operation.bodyParam != null) {
            allowed.add(operation.bodyParam);
        }
        allowed.add("body");
        return allowed;
    }
}
