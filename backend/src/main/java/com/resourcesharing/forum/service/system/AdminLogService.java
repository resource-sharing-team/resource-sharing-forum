package com.resourcesharing.forum.service.system;

import com.resourcesharing.forum.common.PageResult;
import com.resourcesharing.forum.service.support.TxSupport;
import com.resourcesharing.forum.service.support.ValueSupport;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
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
        int size = adminSize(params);
        if (jdbc == null) {
            return new PageResult<>(0, List.of(), page, size);
        }
        String type = values.firstNonBlank(params.get("operationType"), params.get("type"));
        StringBuilder where = new StringBuilder("WHERE deleted_at IS NULL");
        List<Object> args = new ArrayList<>();
        if (!type.isBlank()) {
            where.append(" AND operation_type = ?");
            args.add(type);
        }
        long total = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM admin_operation_log
                %s
                """.formatted(where), Long.class, args.toArray());
        args.add((page - 1) * size);
        args.add(size);
        List<Map<String, Object>> list = jdbc.query("""
                SELECT id, admin_id, operation_type, target_type, target_id, content, before_snapshot, after_snapshot, ip, created_at
                FROM admin_operation_log
                %s
                ORDER BY created_at DESC, id DESC
                LIMIT ?, ?
                """.formatted(where), (rs, rowNum) -> values.map(
                "id", rs.getLong("id"),
                "adminId", rs.getLong("admin_id"),
                "operationType", rs.getString("operation_type"),
                "type", rs.getString("operation_type"),
                "targetType", rs.getString("target_type"),
                "target", rs.getString("target_type"),
                "targetId", rs.getObject("target_id"),
                "content", rs.getString("content"),
                "before", rs.getString("before_snapshot"),
                "after", rs.getString("after_snapshot"),
                "result", "执行成功",
                "ip", rs.getString("ip") == null ? "-" : rs.getString("ip"),
                "date", values.date(rs.getObject("created_at", java.time.LocalDateTime.class)),
                "time", String.valueOf(rs.getObject("created_at", java.time.LocalDateTime.class))
        ), args.toArray());
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

    private int adminSize(Map<String, String> params) {
        String requested = params == null ? null : values.firstNonBlank(params.get("size"), params.get("pageSize"));
        return Math.max(1, Math.min(100, values.intValue(requested, 8)));
    }
}
