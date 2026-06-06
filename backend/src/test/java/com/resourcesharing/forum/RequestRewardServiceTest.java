package com.resourcesharing.forum;

import com.resourcesharing.forum.common.BusinessException;
import com.resourcesharing.forum.common.ErrorCode;
import com.resourcesharing.forum.service.request.RequestRewardService;
import com.resourcesharing.forum.service.support.ForumLookupService;
import com.resourcesharing.forum.service.support.MappingSupport;
import com.resourcesharing.forum.service.support.TxSupport;
import com.resourcesharing.forum.service.support.ValueSupport;
import com.resourcesharing.forum.service.system.AdminLogService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RequestRewardServiceTest {
    @Test
    void replyRequestRequiresReferencedResourceToBePublished() {
        CapturingJdbcTemplate jdbc = new CapturingJdbcTemplate();
        RequestRewardService service = service(jdbc);

        assertThatThrownBy(() -> service.replyRequest(11L, 7L, Map.of(
                "content", "I found a matching internal resource.",
                "resourceId", 42L
        )))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).errorCode())
                .isEqualTo(ErrorCode.BAD_REQUEST);

        String resourceCheck = jdbc.sqlContaining("FROM resource_info");
        assertThat(resourceCheck).contains(
                "SELECT COUNT(*)",
                "status = 'PUBLISHED'",
                "deleted_at IS NULL"
        );
        assertThat(jdbc.argsFor(resourceCheck)).containsExactly(42L);
        assertThat(jdbc.updateSql()).doesNotContain("INSERT INTO request_reply");
    }

    private static RequestRewardService service(JdbcTemplate jdbc) {
        TxSupport txSupport = new TxSupport(provider(jdbc), provider(null));
        ValueSupport values = new ValueSupport();
        ForumLookupService lookup = new ForumLookupService(txSupport);
        MappingSupport mappings = new MappingSupport(values, lookup);
        AdminLogService adminLogService = new AdminLogService(txSupport, values);
        return new RequestRewardService(txSupport, values, mappings, lookup, null, adminLogService, null);
    }

    private static final class CapturingJdbcTemplate extends JdbcTemplate {
        private final List<Call> calls = new ArrayList<>();
        private final List<String> updates = new ArrayList<>();

        @Override
        public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
            calls.add(new Call(sql, args));
            if (sql.contains("FROM member_profile mp")) {
                return requiredType.cast(5L);
            }
            if (sql.contains("FROM resource_info")) {
                return requiredType.cast(0);
            }
            throw new AssertionError("Unexpected scalar query: " + sql);
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T> T queryForObject(String sql, RowMapper<T> rowMapper, Object... args) {
            calls.add(new Call(sql, args));
            if (sql.contains("FROM request_post")) {
                return (T) Map.of("requesterId", 9L, "status", "ONGOING");
            }
            throw new AssertionError("Unexpected mapped query: " + sql);
        }

        @Override
        public int update(String sql, Object... args) {
            updates.add(sql);
            return 1;
        }

        private String sqlContaining(String text) {
            return calls.stream()
                    .map(Call::sql)
                    .filter(sql -> sql.contains(text))
                    .findFirst()
                    .orElseThrow();
        }

        private Object[] argsFor(String sql) {
            return calls.stream()
                    .filter(call -> call.sql().equals(sql))
                    .findFirst()
                    .map(Call::args)
                    .orElseThrow();
        }

        private List<String> updateSql() {
            return updates;
        }
    }

    private record Call(String sql, Object[] args) {
    }

    private static <T> ObjectProvider<T> provider(T value) {
        return new ObjectProvider<>() {
            @Override
            public T getObject(Object... args) {
                return value;
            }

            @Override
            public T getIfAvailable() {
                return value;
            }

            @Override
            public T getIfUnique() {
                return value;
            }

            @Override
            public T getObject() {
                return value;
            }
        };
    }
}
