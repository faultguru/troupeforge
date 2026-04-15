package com.troupeforge.core.storage;

import java.util.Map;

public record QueryCriteria(
    Map<String, Object> attributeEquals,
    int limit,
    int offset,
    String sortBy,
    SortDirection sortDirection
) {
    public static QueryCriteria all() {
        return new QueryCriteria(Map.of(), 1000, 0, null, SortDirection.ASC);
    }
}
