package com.resourcesharing.forum;

import com.resourcesharing.forum.common.BusinessException;
import com.resourcesharing.forum.service.point.PointManager;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=",
        "forum.upload.root-dir=target/test-uploads"
})
class PointManagerIntegrationTest {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("resource_sharing_forum_point_it")
            .withUsername("forum")
            .withPassword("forum");

    private final JdbcTemplate jdbcTemplate;
    private final PointManager pointManager;

    PointManagerIntegrationTest(JdbcTemplate jdbcTemplate, PointManager pointManager) {
        this.jdbcTemplate = jdbcTemplate;
        this.pointManager = pointManager;
    }

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", MYSQL::getDriverClassName);
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.locations", () -> "classpath:db/migration");
    }

    @Test
    void freezesAndRefundsRequestRewardWithPointFlow() {
        Long requesterId = createMember(100);
        Long requestId = createRequest(requesterId, 30);

        pointManager.freezeForRequest(requesterId, 30, requestId);

        Map<String, Object> frozen = pointAccount(requesterId);
        assertThat(frozen.get("current")).isEqualTo(100);
        assertThat(frozen.get("frozen")).isEqualTo(30);
        assertThat(flowCount(requestId, "FREEZE")).isEqualTo(1);

        pointManager.refundRequest(requestId);

        Map<String, Object> refunded = pointAccount(requesterId);
        assertThat(refunded.get("current")).isEqualTo(100);
        assertThat(refunded.get("frozen")).isEqualTo(0);
        assertThat(flowCount(requestId, "UNFREEZE")).isEqualTo(1);

        assertThatThrownBy(() -> pointManager.refundRequest(requestId))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void transfersRewardOnceWithBothPointFlows() {
        Long requesterId = createMember(120);
        Long winnerId = createMember(10);
        Long requestId = createRequest(requesterId, 40);

        pointManager.freezeForRequest(requesterId, 40, requestId);
        pointManager.transferReward(requestId, winnerId);

        Map<String, Object> requester = pointAccount(requesterId);
        Map<String, Object> winner = pointAccount(winnerId);
        assertThat(requester.get("current")).isEqualTo(80);
        assertThat(requester.get("frozen")).isEqualTo(0);
        assertThat(winner.get("current")).isEqualTo(50);
        assertThat(flowCount(requestId, "TRANSFER_OUT")).isEqualTo(1);
        assertThat(flowCount(requestId, "TRANSFER_IN")).isEqualTo(1);

        assertThatThrownBy(() -> pointManager.transferReward(requestId, winnerId))
                .isInstanceOf(BusinessException.class);
    }

    private Long createMember(int points) {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        Long accountId = insertAndReturnKey("""
                INSERT INTO user_account(username, email, password_hash, role, status)
                VALUES (?, ?, '{noop}password', 'USER', 'NORMAL')
                """, "point_" + suffix, "point_" + suffix + "@example.com");
        Long memberId = insertAndReturnKey("""
                INSERT INTO member_profile(account_id, nickname, gender)
                VALUES (?, ?, 'UNKNOWN')
                """, accountId, "point-member-" + suffix);
        Long levelId = jdbcTemplate.queryForObject("SELECT MIN(id) FROM membership_level", Long.class);
        jdbcTemplate.update("""
                INSERT INTO member_point_account(member_id, level_id, current_points, frozen_points, total_earned_points)
                VALUES (?, ?, ?, 0, ?)
                """, memberId, levelId, points, points);
        return memberId;
    }

    private Long createRequest(Long requesterId, int rewardPoints) {
        return insertAndReturnKey("""
                INSERT INTO request_post(requester_id, category_id, title, content, expected_format, reward_points, status)
                VALUES (?, NULL, ?, ?, 'zip', ?, 'ONGOING')
                """, requesterId, "Need point manager test data",
                "Need a complete request reward transaction test payload.", rewardPoints);
    }

    private Map<String, Object> pointAccount(Long memberId) {
        return jdbcTemplate.queryForMap("""
                SELECT current_points AS current, frozen_points AS frozen
                FROM member_point_account
                WHERE member_id = ?
                """, memberId);
    }

    private int flowCount(Long requestId, String flowType) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM point_flow
                WHERE related_type = 'REQUEST_POST'
                  AND related_id = ?
                  AND flow_type = ?
                  AND deleted_at IS NULL
                """, Integer.class, requestId, flowType);
        return count == null ? 0 : count;
    }

    private Long insertAndReturnKey(String sql, Object... args) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < args.length; i++) {
                statement.setObject(i + 1, args[i]);
            }
            return statement;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }
}
