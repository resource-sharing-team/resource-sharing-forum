package com.resourcesharing.forum.common;

import java.util.List;

public record PageResult<T>(List<T> records, long total, int page, int pageSize) {
    public static <T> PageResult<T> empty(PageQuery query) {
        return new PageResult<>(List.of(), 0, query.page(), query.pageSize());
    }
}

