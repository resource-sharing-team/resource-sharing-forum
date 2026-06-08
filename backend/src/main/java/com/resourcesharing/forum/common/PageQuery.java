package com.resourcesharing.forum.common;

public record PageQuery(int page, int size, String sortField, String sortOrder) {

    public PageQuery {
        page = page <= 0 ? 1 : page;
        size = size <= 0 ? 20 : Math.min(size, 100);
        sortField = sortField == null || sortField.isBlank() ? "createdAt" : sortField;
        sortOrder = "asc".equalsIgnoreCase(sortOrder) ? "asc" : "desc";
    }

    public int pageSize() {
        return size;
    }
}

