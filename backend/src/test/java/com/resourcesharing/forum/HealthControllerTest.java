package com.resourcesharing.forum;

import com.resourcesharing.forum.common.ApiResponse;
import com.resourcesharing.forum.controller.HealthController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class HealthControllerTest {

    @Test
    void healthReturnsUpWhenDatabaseIsNotConfiguredForSmokeTests() {
        ObjectProvider<JdbcTemplate> provider = mockProvider(null);

        ResponseEntity<ApiResponse<Map<String, Object>>> response = new HealthController(provider).health();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(200);
        assertThat(response.getBody().data()).containsEntry("status", "UP");
        assertThat(response.getBody().data()).containsEntry("database", "UNCONFIGURED");
    }

    @Test
    void healthChecksDatabaseWhenJdbcTemplateExists() {
        JdbcTemplate jdbcTemplate = new StubJdbcTemplate(null);
        ObjectProvider<JdbcTemplate> provider = mockProvider(jdbcTemplate);

        ResponseEntity<ApiResponse<Map<String, Object>>> response = new HealthController(provider).health();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(200);
        assertThat(response.getBody().data()).containsEntry("status", "UP");
        assertThat(response.getBody().data()).containsEntry("database", "UP");
    }

    @Test
    void healthReturnsServiceUnavailableWhenDatabaseCheckFails() {
        JdbcTemplate jdbcTemplate = new StubJdbcTemplate(
                new DataAccessResourceFailureException("database unavailable"));
        ObjectProvider<JdbcTemplate> provider = mockProvider(jdbcTemplate);

        ResponseEntity<ApiResponse<Map<String, Object>>> response = new HealthController(provider).health();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(503);
        assertThat(response.getBody().message()).isEqualTo("database unavailable");
        assertThat(response.getBody().data()).containsEntry("status", "DOWN");
        assertThat(response.getBody().data()).containsEntry("database", "DOWN");
    }

    private static ObjectProvider<JdbcTemplate> mockProvider(JdbcTemplate jdbcTemplate) {
        return new StubObjectProvider(jdbcTemplate);
    }

    private record StubObjectProvider(JdbcTemplate jdbcTemplate) implements ObjectProvider<JdbcTemplate> {
        @Override
        public JdbcTemplate getObject(Object... args) {
            return jdbcTemplate;
        }

        @Override
        public JdbcTemplate getIfAvailable() {
            return jdbcTemplate;
        }

        @Override
        public JdbcTemplate getIfUnique() {
            return jdbcTemplate;
        }

        @Override
        public JdbcTemplate getObject() {
            return jdbcTemplate;
        }
    }

    private static final class StubJdbcTemplate extends JdbcTemplate {
        private final RuntimeException failure;

        private StubJdbcTemplate(RuntimeException failure) {
            this.failure = failure;
        }

        @Override
        public <T> T queryForObject(String sql, Class<T> requiredType) {
            if (failure != null) {
                throw failure;
            }
            return requiredType.cast(1);
        }
    }
}
