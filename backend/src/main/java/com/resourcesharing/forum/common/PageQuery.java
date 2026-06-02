package com.resourcesharing.forum.common;

public record PageQuery(int page, int pageSize, String sortField, String sortOrder) {

    public PageQuery {
        page = page <= 0 ? 1 : page;
        pageSize = pageSize <= 0 ? 10 : Math.min(pageSize, 100);
        sortField = sortField == null || sortField.isBlank() ? "createdAt" : sortField;
        sortOrder = "asc".equalsIgnoreCase(sortOrder) ? "asc" : "desc";
    }
}

