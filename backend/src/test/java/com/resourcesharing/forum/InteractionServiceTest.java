package com.resourcesharing.forum;

import com.resourcesharing.forum.common.BusinessException;
import com.resourcesharing.forum.common.ErrorCode;
import com.resourcesharing.forum.service.interaction.InteractionService;
import com.resourcesharing.forum.service.support.ContentModerationService;
import com.resourcesharing.forum.service.support.ForumLookupService;
import com.resourcesharing.forum.service.support.MappingSupport;
import com.resourcesharing.forum.service.support.TxSupport;
import com.resourcesharing.forum.service.support.ValueSupport;
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

class InteractionServiceTest {
    @Test
    void addCommentRequiresPublishedResourceTarget() {
        CapturingJdbcTemplate jdbc = new CapturingJdbcTemplate();
        InteractionService service = service(jdbc);

        assertThatThrownBy(() -> service.addComment(7L, Map.of(
                "targetType", "RESOURCE",
                "targetId", 42L,
                "content", "resource is offline"
        )))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).errorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);

        String targetQuery = jdbc.sqlContaining("FROM resource_info");
        assertThat(targetQuery).contains(
                "SELECT publisher_id",
                "status = 'PUBLISHED'",
                "deleted_at IS NULL"
        );
        assertThat(jdbc.argsFor(targetQuery)).containsExactly(42L);
    }

    @Test
    void replyRequiresParentCommentOnSameTarget() {
        CapturingJdbcTemplate jdbc = new CapturingJdbcTemplate();
        InteractionService service = service(jdbc);

        assertThatThrownBy(() -> service.addComment(7L, Map.of(
                "targetType", "RESOURCE",
                "targetId", 42L,
                "parentId", 9L,
                "content", "reply to another target"
        )))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).errorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);

        String parentQuery = jdbc.sqlContaining("FROM comment_info");
        assertThat(parentQuery).contains(
                "target_type = ?",
                "target_id = ?",
                "parent_id IS NULL",
                "status = 'ACTIVE'",
                "deleted_at IS NULL"
        );
        assertThat(jdbc.argsFor(parentQuery)).containsExactly(9L, "RESOURCE", 42L);
    }

    @Test
    void deleteCommentSoftDeletesAndDecrementsResourceCommentCount() {
        CapturingJdbcTemplate jdbc = new CapturingJdbcTemplate();
        InteractionService service = service(jdbc);

        service.deleteComment(17L, 7L);

        String lockQuery = jdbc.sqlContaining("FROM comment_info");
        assertThat(lockQuery).contains(
                "SELECT target_type, target_id",
                "member_id = ?",
                "status = 'ACTIVE'",
                "FOR UPDATE"
        );
        assertThat(jdbc.argsFor(lockQuery)).containsExactly(17L, 5L);
        assertThat(jdbc.updateSql()).anySatisfy(sql -> assertThat(sql).contains(
                "UPDATE comment_info",
                "SET status = 'DELETED'",
                "deleted_at = NOW(3)"
        ));
        assertThat(jdbc.updateSql()).anySatisfy(sql -> assertThat(sql).contains(
                "UPDATE resource_info",
                "comment_count = GREATEST(comment_count - 1, 0)"
        ));
    }

    @Test
    void likeCommentRequiresActiveCommentBeforeWritingInteraction() {
        CapturingJdbcTemplate jdbc = new CapturingJdbcTemplate();
        InteractionService service = service(jdbc);

        assertThatThrownBy(() -> service.likeComment(17L, 7L))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).errorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);

        String activeCommentQuery = jdbc.sqlContaining("SELECT COUNT(*)");
        assertThat(activeCommentQuery).contains(
                "FROM comment_info",
                "status = 'ACTIVE'",
                "deleted_at IS NULL"
        );
        assertThat(jdbc.argsFor(activeCommentQuery)).containsExactly(17L);
        assertThat(jdbc.updateSql()).doesNotContain("INSERT INTO user_interaction");
    }

    @Test
    void addCommentRejectsSensitiveContentBeforeWritingComment() {
        CapturingJdbcTemplate jdbc = new CapturingJdbcTemplate();
        InteractionService service = service(jdbc, new ContentModerationService(new TxSupport(provider(null), provider(null))) {
            @Override
            public void requireClean(String content) {
                throw new BusinessException(ErrorCode.SENSITIVE_CONTENT, ErrorCode.SENSITIVE_CONTENT.message());
            }
        });

        assertThatThrownBy(() -> service.addComment(7L, Map.of(
                "targetType", "RESOURCE",
                "targetId", 42L,
                "content", "sensitive content"
        )))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).errorCode())
                .isEqualTo(ErrorCode.SENSITIVE_CONTENT);

        assertThat(jdbc.updateSql()).isEmpty();
    }

    private static InteractionService service(JdbcTemplate jdbc) {
        return service(jdbc, new ContentModerationService(new TxSupport(provider(null), provider(null))));
    }

    private static InteractionService service(JdbcTemplate jdbc, ContentModerationService contentModerationService) {
        TxSupport txSupport = new TxSupport(provider(jdbc), provider(null));
        ValueSupport values = new ValueSupport();
        ForumLookupService lookup = new ForumLookupService(txSupport);
        MappingSupport mappings = new MappingSupport(values, lookup);
        return new InteractionService(txSupport, values, mappings, lookup, null, null, contentModerationService);
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
            if (sql.contains("FROM comment_info") && sql.contains("SELECT COUNT(*)")) {
                return requiredType.cast(0);
            }
            throw new EmptyResultDataAccessException(1);
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T> T queryForObject(String sql, RowMapper<T> rowMapper, Object... args) {
            calls.add(new Call(sql, args));
            if (sql.contains("FROM comment_info") && sql.contains("SELECT target_type, target_id")) {
                return (T) Map.of("targetType", "RESOURCE", "targetId", 42L);
            }
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
