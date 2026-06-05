package com.resourcesharing.forum.service.system;

import com.resourcesharing.forum.common.PageResult;
import com.resourcesharing.forum.service.support.TxSupport;
import com.resourcesharing.forum.service.support.ValueSupport;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class AdminLogService {
    private final TxSupport txSupport;
    private final ValueSupport values;

    public AdminLogService(TxSupport txSupport, ValueSupport values) {
        this.txSupport = txSupport;
        this.values = values;
    }

    public PageResult<Map<String, Object>> adminLogs(Map<String, String> params) {
        JdbcTemplate jdbc = txSupport.jdbc();
        int page = values.page(params);
        int size = values.size(params);
        if (jdbc == null) {
            return new PageResult<>(0, List.of(), page, size);
        }
        long total = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM admin_operation_log
                WHERE deleted_at IS NULL
                """, Long.class);
        List<Map<String, Object>> list = jdbc.query("""
                SELECT id, admin_id, operation_type, target_type, target_id, content, create_time
                FROM admin_operation_log
                WHERE deleted_at IS NULL
                ORDER BY create_time DESC, id DESC
                LIMIT ?, ?
                """, (rs, rowNum) -> values.map(
                "id", rs.getLong("id"),
                "adminId", rs.getLong("admin_id"),
                "operationType", rs.getString("operation_type"),
                "targetType", rs.getString("target_type"),
                "targetId", rs.getObject("target_id"),
                "content", rs.getString("content"),
                "date", values.date(rs.getObject("create_time", java.time.LocalDateTime.class))
        ), (page - 1) * size, size);
        return new PageResult<>(total, list, page, size);
    }

    public void record(Long adminProfileId, String operationType, String targetType, Long targetId, String before, String after) {
        JdbcTemplate jdbc = txSupport.jdbc();
        if (jdbc == null) {
            return;
        }
        jdbc.update("""
                INSERT INTO admin_operation_log(admin_id, operation_type, target_type, target_id, content, before_snapshot, after_snapshot)
                VALUES (?, ?, ?, ?, ?, JSON_OBJECT('status', ?), JSON_OBJECT('status', ?))
                """, adminProfileId, operationType, targetType, targetId, operationType, before, after);
    }
}
