package com.resourcesharing.forum.common;

import java.util.List;

public record PageResult<T>(long total, List<T> list, int page, int size) {
    public static <T> PageResult<T> empty(PageQuery query) {
        return new PageResult<>(0, List.of(), query.page(), query.size());
    }

    public int pageSize() {
        return size;
    }
}

