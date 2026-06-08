package com.resourcesharing.forum.service.system;

import com.resourcesharing.forum.service.support.ForumLookupService;
import com.resourcesharing.forum.service.support.TxSupport;
import com.resourcesharing.forum.service.support.ValueSupport;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AdminSystemService {
    private final TxSupport txSupport;
    private final ValueSupport values;
    private final ForumLookupService lookup;
    private final AdminLogService adminLogService;

    public AdminSystemService(
            TxSupport txSupport,
            ValueSupport values,
            ForumLookupService lookup,
            AdminLogService adminLogService
    ) {
        this.txSupport = txSupport;
        this.values = values;
        this.lookup = lookup;
        this.adminLogService = adminLogService;
    }

    public Map<String, Object> systemConfig() {
        JdbcTemplate jdbc = txSupport.jdbc();
        if (jdbc == null) {
            return values.map("upload.max_file_size_mb", "100", "page.default_size", "20");
        }
        List<Map<String, Object>> rows = jdbc.query("""
                SELECT config_key, config_value, value_type, description, is_sensitive, is_enabled
                FROM system_config
                WHERE is_enabled = 1
                ORDER BY id ASC
                """, (rs, rowNum) -> values.map(
                "key", rs.getString("config_key"),
                "value", rs.getInt("is_sensitive") == 1 ? "******" : rs.getString("config_value"),
                "valueType", rs.getString("value_type"),
                "description", rs.getString("description")
        ));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", rows);
        for (Map<String, Object> row : rows) {
            result.put(String.valueOf(row.get("key")), row.get("value"));
        }
        return result;
    }

    public Map<String, Object> updateSystemConfig(Long adminAccountId, Map<String, Object> request) {
        JdbcTemplate jdbc = txSupport.jdbc();
        if (jdbc == null) {
            return request;
        }
        return txSupport.required(() -> {
            Long adminProfileId = lookup.adminProfileId(adminAccountId);
            if (request.containsKey("key")) {
                upsertConfig(
                        jdbc,
                        String.valueOf(request.get("key")),
                        String.valueOf(request.getOrDefault("value", "")),
                        String.valueOf(request.getOrDefault("valueType", "STRING"))
                );
            } else {
                for (Map.Entry<String, Object> entry : request.entrySet()) {
                    if (entry.getValue() != null) {
                        upsertConfig(jdbc, entry.getKey(), String.valueOf(entry.getValue()), "STRING");
                    }
                }
            }
            adminLogService.record(adminProfileId, "SYSTEM_CONFIG_UPDATE", "SYSTEM_CONFIG", null, "UPDATED", "UPDATED");
            return systemConfig();
        });
    }

    public Map<String, Object> refreshCache(Long adminAccountId) {
        adminLogService.record(lookup.adminProfileId(adminAccountId), "CACHE_REFRESH", "SYSTEM_CONFIG", null, "CACHE", "REFRESHED");
        return values.map("ok", true, "status", "REFRESHED");
    }

    private void upsertConfig(JdbcTemplate jdbc, String key, String value, String valueType) {
        String safeType = List.of("STRING", "INTEGER", "BOOLEAN", "JSON").contains(valueType) ? valueType : "STRING";
        jdbc.update("""
                INSERT INTO system_config(config_key, config_value, value_type, description, is_enabled)
                VALUES (?, ?, ?, 'Admin configuration update', 1)
                ON DUPLICATE KEY UPDATE config_value = VALUES(config_value), value_type = VALUES(value_type), is_enabled = 1
                """, key, value, safeType);
    }
}
