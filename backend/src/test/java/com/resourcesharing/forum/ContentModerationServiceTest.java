package com.resourcesharing.forum;

import com.resourcesharing.forum.common.BusinessException;
import com.resourcesharing.forum.common.ErrorCode;
import com.resourcesharing.forum.service.support.ContentModerationService;
import com.resourcesharing.forum.service.support.TxSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ContentModerationServiceTest {
    @Test
    void rejectsContentBySensitiveWordMatchType() {
        JdbcTemplate jdbc = new SensitiveWordJdbcTemplate();
        ContentModerationService service = new ContentModerationService(new TxSupport(provider(jdbc), provider(null)));

        assertSensitive(() -> service.requireClean("exact bad"));
        assertSensitive(() -> service.requireClean("this has a BLOCKED token"));
        assertSensitive(() -> service.requireClean("regex A123 token"));
        service.requireClean("ordinary clean content");
    }

    private static void assertSensitive(ThrowingCallable callable) {
        assertThatThrownBy(callable::call)
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).errorCode())
                .isEqualTo(ErrorCode.SENSITIVE_CONTENT);
    }

    private interface ThrowingCallable {
        void call();
    }

    private static final class SensitiveWordJdbcTemplate extends JdbcTemplate {
        @Override
        public List<Map<String, Object>> queryForList(String sql) {
            return List.of(
                    Map.of("word", "exact bad", "match_type", "EXACT"),
                    Map.of("word", "blocked", "match_type", "CONTAINS"),
                    Map.of("word", "A\\d{3}", "match_type", "REGEX"),
                    Map.of("word", "[", "match_type", "REGEX")
            );
        }
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
