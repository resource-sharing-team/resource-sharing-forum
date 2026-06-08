package com.resourcesharing.forum;

import com.resourcesharing.forum.common.BusinessException;
import com.resourcesharing.forum.common.ErrorCode;
import com.resourcesharing.forum.service.resource.ResourceQueryService;
import com.resourcesharing.forum.service.support.ForumLookupService;
import com.resourcesharing.forum.service.support.MappingSupport;
import com.resourcesharing.forum.service.support.TxSupport;
import com.resourcesharing.forum.service.support.ValueSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResourceQueryServiceTest {
    @Test
    void resourceDetailQueryAllowsOnlyPublishedOwnerOrAdministratorVisibility() {
        CapturingJdbcTemplate jdbc = new CapturingJdbcTemplate();
        ResourceQueryService service = service(jdbc);

        assertThatThrownBy(() -> service.resource(42L, 7L))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).errorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);

        assertThat(jdbc.sql()).contains(
                "r.status = 'PUBLISHED'",
                "FROM member_profile current_mp",
                "current_mp.account_id = ?",
                "current_mp.id = r.publisher_id",
                "FROM administrator_profile ap",
                "admin_ua.role IN ('ADMIN', 'SUPER_ADMIN', 'AUDITOR')",
                "locked_until IS NULL OR current_ua.locked_until <= NOW(3)",
                "locked_until IS NULL OR admin_ua.locked_until <= NOW(3)"
        );
        assertThat(jdbc.args()).containsExactly(42L, 7L, 7L);
    }

    private static ResourceQueryService service(JdbcTemplate jdbc) {
        TxSupport txSupport = new TxSupport(provider(jdbc), provider(null));
        ValueSupport values = new ValueSupport();
        ForumLookupService lookup = new ForumLookupService(txSupport);
        MappingSupport mappings = new MappingSupport(values, lookup);
        return new ResourceQueryService(txSupport, values, mappings);
    }

    private static final class CapturingJdbcTemplate extends JdbcTemplate {
        private String sql;
        private Object[] args;

        @Override
        public <T> T queryForObject(String sql, RowMapper<T> rowMapper, Object... args) {
            this.sql = sql;
            this.args = args;
            throw new EmptyResultDataAccessException(1);
        }

        private String sql() {
            return sql;
        }

        private Object[] args() {
            return args;
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