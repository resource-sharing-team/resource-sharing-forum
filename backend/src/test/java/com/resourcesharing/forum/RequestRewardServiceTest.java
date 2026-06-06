package com.resourcesharing.forum;

import com.resourcesharing.forum.common.BusinessException;
import com.resourcesharing.forum.common.ErrorCode;
import com.resourcesharing.forum.service.request.RequestRewardService;
import com.resourcesharing.forum.service.support.ContentModerationService;
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
    void createRequestValidatesTitleAndContentBeforeWriting() {
        CapturingJdbcTemplate jdbc = new CapturingJdbcTemplate();
        RequestRewardService service = service(jdbc);

        assertThatThrownBy(() -> service.createRequest(7L, Map.of(
                "title", "Need",
                "content", "Need a complete backend project package"
        )))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).errorCode())
                .isEqualTo(ErrorCode.BAD_REQUEST);

        assertThat(jdbc.updateSql()).isEmpty();
    }

    @Test
    void createRequestRejectsSensitiveContentBeforeWriting() {
        CapturingJdbcTemplate jdbc = new CapturingJdbcTemplate();
        RequestRewardService service = service(jdbc, moderationRejecting("blocked"));

        assertThatThrownBy(() -> service.createRequest(7L, Map.of(
                "title", "Need course backend",
                "content", "Need a blocked backend project package with docs"
        )))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).errorCode())
                .isEqualTo(ErrorCode.SENSITIVE_CONTENT);

        assertThat(jdbc.updateSql()).isEmpty();
    }

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

    @Test
    void replyRequestRequiresContentResourceOrExternalUrlBeforeWriting() {
        CapturingJdbcTemplate jdbc = new CapturingJdbcTemplate();
        RequestRewardService service = service(jdbc);

        assertThatThrownBy(() -> service.replyRequest(11L, 7L, Map.of("content", "  ")))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).errorCode())
                .isEqualTo(ErrorCode.BAD_REQUEST);

        assertThat(jdbc.updateSql()).isEmpty();
    }

    @Test
    void replyRequestRejectsSensitiveContentBeforeWriting() {
        CapturingJdbcTemplate jdbc = new CapturingJdbcTemplate();
        RequestRewardService service = service(jdbc, moderationRejecting("blocked"));

        assertThatThrownBy(() -> service.replyRequest(11L, 7L, Map.of("content", "blocked reply")))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).errorCode())
                .isEqualTo(ErrorCode.SENSITIVE_CONTENT);

        assertThat(jdbc.updateSql()).isEmpty();
    }

    private static RequestRewardService service(JdbcTemplate jdbc) {
        return service(jdbc, new ContentModerationService(new TxSupport(provider(null), provider(null))));
    }

    private static RequestRewardService service(JdbcTemplate jdbc, ContentModerationService contentModerationService) {
        TxSupport txSupport = new TxSupport(provider(jdbc), provider(null));
        ValueSupport values = new ValueSupport();
        ForumLookupService lookup = new ForumLookupService(txSupport);
        MappingSupport mappings = new MappingSupport(values, lookup);
        AdminLogService adminLogService = new AdminLogService(txSupport, values);
        return new RequestRewardService(txSupport, values, mappings, lookup, null, adminLogService, null, contentModerationService);
    }

    private static ContentModerationService moderationRejecting(String token) {
        return new ContentModerationService(new TxSupport(provider(null), provider(null))) {
            @Override
            public void requireClean(String content) {
                if (content != null && content.contains(token)) {
                    throw new BusinessException(ErrorCode.SENSITIVE_CONTENT, ErrorCode.SENSITIVE_CONTENT.message());
                }
            }
        };
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
