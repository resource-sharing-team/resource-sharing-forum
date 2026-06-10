package com.resourcesharing.forum;

import com.resourcesharing.forum.common.BusinessException;
import com.resourcesharing.forum.common.ErrorCode;
import com.resourcesharing.forum.service.interaction.CommentTreeService;
import com.resourcesharing.forum.service.notification.NotificationDispatcher;
import com.resourcesharing.forum.service.point.PointManager;
import com.resourcesharing.forum.service.point.PointRuleService;
import com.resourcesharing.forum.service.resource.ResourceQueryService;
import com.resourcesharing.forum.service.resource.ResourceService;
import com.resourcesharing.forum.service.support.ContentModerationService;
import com.resourcesharing.forum.service.support.ForumLookupService;
import com.resourcesharing.forum.service.support.MappingSupport;
import com.resourcesharing.forum.service.support.TxSupport;
import com.resourcesharing.forum.service.support.ValueSupport;
import com.resourcesharing.forum.service.system.AdminLogService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResourceServiceTest {
    @Test
    void publishResourceValidatesTitleDescriptionAndTagsBeforeWriting() {
        CapturingJdbcTemplate jdbc = new CapturingJdbcTemplate();
        ResourceService service = service(jdbc);

        assertThatThrownBy(() -> service.publishResource(7L, Map.of(
                "title", "Bad",
                "description", "A complete backend resource package.",
                "categoryId", 11L,
                "tags", "java"
        ), null))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).errorCode())
                .isEqualTo(ErrorCode.BAD_REQUEST);

        assertThat(jdbc.updateSql()).isEmpty();
    }

    @Test
    void publishResourceRejectsSensitiveContentBeforeWriting() {
        CapturingJdbcTemplate jdbc = new CapturingJdbcTemplate();
        ResourceService service = service(jdbc, moderationRejecting("blocked"));

        assertThatThrownBy(() -> service.publishResource(7L, Map.of(
                "title", "Useful Java package",
                "description", "A blocked backend resource package with examples.",
                "categoryId", 11L,
                "tags", "java"
        ), null))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).errorCode())
                .isEqualTo(ErrorCode.SENSITIVE_CONTENT);

        assertThat(jdbc.updateSql()).isEmpty();
    }

    @Test
    void publishResourceRequiresEnabledSecondLevelCategoryBeforeWriting() {
        CapturingJdbcTemplate jdbc = new CapturingJdbcTemplate();
        jdbc.publishableCategoryCount = 0;
        ResourceService service = service(jdbc);

        assertThatThrownBy(() -> service.publishResource(7L, validRequest(), null))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).errorCode())
                .isEqualTo(ErrorCode.BAD_REQUEST);

        String categoryCheck = jdbc.sqlContaining("FROM resource_category");
        assertThat(categoryCheck).contains(
                "level_no = 2",
                "status = 'ENABLED'",
                "deleted_at IS NULL"
        );
        assertThat(jdbc.argsFor(categoryCheck)).containsExactly(11L);
        assertThat(jdbc.updateSql()).isEmpty();
    }

    @Test
    void publishResourceRejectsDisabledExistingTagBeforeWriting() {
        CapturingJdbcTemplate jdbc = new CapturingJdbcTemplate();
        jdbc.tagStatus = "DISABLED";
        ResourceService service = service(jdbc);

        assertThatThrownBy(() -> service.publishResource(7L, validRequest(), null))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).errorCode())
                .isEqualTo(ErrorCode.BAD_REQUEST);

        String tagCheck = jdbc.sqlContaining("FROM tag_info");
        assertThat(tagCheck).contains(
                "SELECT status",
                "tag_name = ?",
                "deleted_at IS NULL"
        );
        assertThat(jdbc.argsFor(tagCheck)).containsExactly("java");
        assertThat(jdbc.updateSql()).isEmpty();
    }

    private static Map<String, Object> validRequest() {
        return Map.of(
                "title", "Useful Java package",
                "description", "A complete backend resource package with examples.",
                "categoryId", 11L,
                "tags", "java"
        );
    }

    private static ResourceService service(JdbcTemplate jdbc) {
        return service(jdbc, new ContentModerationService(new TxSupport(provider(null), provider(null))));
    }

    private static ResourceService service(JdbcTemplate jdbc, ContentModerationService contentModerationService) {
        TxSupport txSupport = new TxSupport(provider(jdbc), provider(null));
        ValueSupport values = new ValueSupport();
        ForumLookupService lookup = new ForumLookupService(txSupport);
        MappingSupport mappings = new MappingSupport(values, lookup);
        CommentTreeService commentTreeService = new CommentTreeService(txSupport, mappings);
        ResourceQueryService queryService = new ResourceQueryService(txSupport, values, mappings, commentTreeService);
        AdminLogService adminLogService = new AdminLogService(txSupport, values);
        return new ResourceService(txSupport, values, lookup, queryService, adminLogService, null, contentModerationService,
                null, new PointRuleService(txSupport, values));
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
        private int publishableCategoryCount = 1;
        private String tagStatus = "ENABLED";

        @Override
        public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
            calls.add(new Call(sql, args));
            if (sql.contains("FROM member_profile mp")) {
                return requiredType.cast(5L);
            }
            if (sql.contains("FROM resource_category")) {
                return requiredType.cast(publishableCategoryCount);
            }
            if (sql.contains("FROM tag_info")) {
                return requiredType.cast(tagStatus);
            }
            throw new AssertionError("Unexpected scalar query: " + sql);
        }

        @Override
        public <T> T queryForObject(String sql, RowMapper<T> rowMapper, Object... args) {
            calls.add(new Call(sql, args));
            throw new EmptyResultDataAccessException(1);
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
