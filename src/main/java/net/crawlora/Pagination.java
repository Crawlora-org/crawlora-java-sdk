package net.crawlora;

import java.util.List;
import java.util.Map;

/** Pagination helpers shared by {@link CrawloraClient#paginate} / {@code paginateItems}. */
public final class Pagination {
    private Pagination() {}

    static final List<String> PAGE_PARAM_NAMES = List.of("page", "offset");

    /** First page/offset query parameter an operation exposes, or {@code null}. */
    public static String detectPageParam(Operation operation) {
        for (String candidate : PAGE_PARAM_NAMES) {
            for (QueryParam p : operation.queryParams) {
                if (p.name.equals(candidate)) {
                    return candidate;
                }
            }
        }
        return null;
    }

    /**
     * A page is empty when its {@code data} array (Crawlora envelope) or the
     * page itself is empty/blank.
     */
    @SuppressWarnings("unchecked")
    public static boolean pageEmpty(Object response) {
        Object data = response;
        if (response instanceof Map && ((Map<String, Object>) response).containsKey("data")) {
            data = ((Map<String, Object>) response).get("data");
        }
        if (data == null) {
            return true;
        }
        if (data instanceof List) {
            return ((List<?>) data).isEmpty();
        }
        if (data instanceof Map) {
            return ((Map<?, ?>) data).isEmpty();
        }
        if (data instanceof String) {
            return ((String) data).isEmpty();
        }
        if (data instanceof Boolean) {
            return !((Boolean) data);
        }
        return false;
    }

    public static long defaultStart(String pageParam) {
        return "offset".equals(pageParam) ? 0 : 1;
    }

    /**
     * Default item extractor: the response's {@code data} list (Crawlora
     * envelope), or the response itself when it is already a list.
     */
    @SuppressWarnings("unchecked")
    public static List<Object> defaultItems(Object response) {
        if (response instanceof Map) {
            Object data = ((Map<String, Object>) response).get("data");
            if (data instanceof List) {
                return (List<Object>) data;
            }
        }
        if (response instanceof List) {
            return (List<Object>) response;
        }
        return List.of();
    }
}
