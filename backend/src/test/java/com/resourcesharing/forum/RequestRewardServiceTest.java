package com.resourcesharing.forum;

import com.resourcesharing.forum.common.BusinessException;
import com.resourcesharing.forum.common.ErrorCode;
import com.resourcesharing.forum.service.request.RequestRewardService;
import com.resourcesharing.forum.service.point.PointManager;
import com.resourcesharing.forum.service.support.ContentModerationService;
import com.resourcesharing.forum.service.support.ForumLookupService;
import com.resourcesharing.forum.service.support.MappingSupport;
import com.resourcesharing.forum.service.support.TxSupport;
import com.resourcesharing.forum.service.support.ValueSupport;
import com.resourcesharing.forum.service.system.AdminLogService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.KeyHolder;

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

    @Test
    void createFreeRequestDoesNotFreezeRewardPoints() {
        CapturingJdbcTemplate jdbc = new CapturingJdbcTemplate(100, 0, 100);
        RecordingPointManager points = new RecordingPointManager();
        RequestRewardService service = service(jdbc, new ContentModerationService(new TxSupport(provider(null), provider(null))), points);

        service.createRequest(7L, Map.of(
                "title", "Need course backend template",
                "content", "Need a complete backend project package with deployment notes.",
                "rewardType", "FREE",
                "rewardPoints", 50
        ));

        assertThat(points.frozenPoints()).isEmpty();
    }

    @Test
    void createPointRewardRequestFreezesFromOnePoint() {
        CapturingJdbcTemplate jdbc = new CapturingJdbcTemplate(100, 0, 100);
        RecordingPointManager points = new RecordingPointManager();
        RequestRewardService service = service(jdbc, new ContentModerationService(new TxSupport(provider(null), provider(null))), points);

        service.createRequest(7L, Map.of(
                "title", "Need course backend template",
                "content", "Need a complete backend project package with deployment notes.",
                "rewardType", "POINT",
                "rewardPoints", 1
        ));

        assertThat(points.frozenPoints()).containsExactly(1);
    }

    @Test
    void createPointRewardRequiresAtLeastOnePoint() {
        CapturingJdbcTemplate jdbc = new CapturingJdbcTemplate(100, 0, 100);
        RequestRewardService service = service(jdbc);

        assertThatThrownBy(() -> service.createRequest(7L, Map.of(
                "title", "Need course backend template",
                "content", "Need a complete backend project package with deployment notes.",
                "rewardType", "POINT",
                "rewardPoints", 0
        )))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).errorCode())
                .isEqualTo(ErrorCode.BAD_REQUEST);

        assertThat(jdbc.updateSql()).isEmpty();
    }

    @Test
    void createPointRewardCannotExceedAvailablePointsOrRewardLimit() {
        RequestRewardService lowBalance = service(new CapturingJdbcTemplate(10, 5, 100));
        assertThatThrownBy(() -> lowBalance.createRequest(7L, Map.of(
                "title", "Need course backend template",
                "content", "Need a complete backend project package with deployment notes.",
                "rewardType", "POINT",
                "rewardPoints", 6
        )))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).errorCode())
                .isEqualTo(ErrorCode.BAD_REQUEST);

        RequestRewardService lowLimit = service(new CapturingJdbcTemplate(100, 0, 20));
        assertThatThrownBy(() -> lowLimit.createRequest(7L, Map.of(
                "title", "Need course backend template",
                "content", "Need a complete backend project package with deployment notes.",
                "rewardType", "POINT",
                "rewardPoints", 21
        )))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).errorCode())
                .isEqualTo(ErrorCode.BAD_REQUEST);
    }

    @Test
    void listRequestsSupportsStatusRewardRangeAndAscendingSort() {
        CapturingJdbcTemplate jdbc = new CapturingJdbcTemplate();
        RequestRewardService service = service(jdbc);

        service.listRequests(Map.of(
                "status", "active",
                "points", "100-500",
                "sort", "points",
                "order", "asc"
        ));

        String listSql = jdbc.listSqlContaining("ORDER BY");
        assertThat(listSql).contains(
                "rp.status = ?",
                "rp.reward_points > 100 AND rp.reward_points <= 500",
                "ORDER BY rp.reward_points ASC"
        );
        assertThat(jdbc.argsFor(listSql)).contains("ONGOING");
    }

    @Test
    void listRequestsSupportsDescendingRewardSortByDefault() {
        CapturingJdbcTemplate jdbc = new CapturingJdbcTemplate();
        RequestRewardService service = service(jdbc);

        service.listRequests(Map.of(
                "sort", "points",
                "order", "desc"
        ));

        String listSql = jdbc.listSqlContaining("ORDER BY");
        assertThat(listSql).contains("ORDER BY rp.reward_points DESC");
    }

    @Test
    void listRequestsSupportsReplySortAndInvalidOrderFallback() {
        CapturingJdbcTemplate jdbc = new CapturingJdbcTemplate();
        RequestRewardService service = service(jdbc);

        service.listRequests(Map.of(
                "sort", "reply",
                "order", "sideways"
        ));

        String listSql = jdbc.listSqlContaining("ORDER BY");
        assertThat(listSql).contains("ORDER BY rp.answer_count DESC");
    }

    @Test
    void listRequestsSupportsSolvedStatusAndRewardRange() {
        CapturingJdbcTemplate jdbc = new CapturingJdbcTemplate();
        RequestRewardService service = service(jdbc);

        service.listRequests(Map.of(
                "status", "solved",
                "points", "500-2000"
        ));

        String listSql = jdbc.listSqlContaining("ORDER BY");
        assertThat(listSql).contains(
                "rp.status = ?",
                "rp.reward_points > 500 AND rp.reward_points <= 2000",
                "ORDER BY rp.create_time DESC, rp.id DESC"
        );
        assertThat(jdbc.argsFor(listSql)).contains("RESOLVED");
    }

    private static RequestRewardService service(JdbcTemplate jdbc) {
        return service(jdbc, new ContentModerationService(new TxSupport(provider(null), provider(null))));
    }

    private static RequestRewardService service(JdbcTemplate jdbc, ContentModerationService contentModerationService) {
        return service(jdbc, contentModerationService, null);
    }

    private static RequestRewardService service(JdbcTemplate jdbc, ContentModerationService contentModerationService, PointManager pointManager) {
        TxSupport txSupport = new TxSupport(provider(jdbc), provider(null));
        ValueSupport values = new ValueSupport();
        ForumLookupService lookup = new ForumLookupService(txSupport);
        MappingSupport mappings = new MappingSupport(values, lookup);
        AdminLogService adminLogService = new AdminLogService(txSupport, values);
        return new RequestRewardService(txSupport, values, mappings, lookup, pointManager, adminLogService, null, contentModerationService);
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
        private final int currentPoints;
        private final int frozenPoints;
        private final int rewardLimit;
        private final List<Call> calls = new ArrayList<>();
        private final List<String> updates = new ArrayList<>();

        private CapturingJdbcTemplate() {
            this(100, 0, 100);
        }

        private CapturingJdbcTemplate(int currentPoints, int frozenPoints, int rewardLimit) {
            this.currentPoints = currentPoints;
            this.frozenPoints = frozenPoints;
            this.rewardLimit = rewardLimit;
        }

        @Override
        public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
            calls.add(new Call(sql, args));
            if (sql.contains("FROM member_profile mp")) {
                return requiredType.cast(5L);
            }
            if (sql.contains("FROM resource_info")) {
                return requiredType.cast(0);
            }
            if (sql.contains("COUNT(*) FROM request_post")) {
                return requiredType.cast(0L);
            }
            if (sql.contains("FROM system_config")) {
                return requiredType.cast("100");
            }
            throw new AssertionError("Unexpected scalar query: " + sql);
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T> T queryForObject(String sql, RowMapper<T> rowMapper, Object... args) {
            calls.add(new Call(sql, args));
            if (sql.contains("FROM member_point_account")) {
                return (T) Map.of("currentPoints", currentPoints, "frozenPoints", frozenPoints, "rewardLimit", rewardLimit);
            }
            if (sql.contains("FROM request_post")) {
                return (T) Map.of("id", 123L, "requesterId", 9L, "status", "ONGOING");
            }
            throw new AssertionError("Unexpected mapped query: " + sql);
        }

        @Override
        public int update(String sql, Object... args) {
            updates.add(sql);
            return 1;
        }

        @Override
        public int update(PreparedStatementCreator psc, KeyHolder generatedKeyHolder) {
            updates.add("INSERT INTO request_post");
            generatedKeyHolder.getKeyList().add(Map.of("id", 123L));
            return 1;
        }

        @Override
        public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
            calls.add(new Call(sql, args));
            return List.of();
        }

        private String sqlContaining(String text) {
            return calls.stream()
                    .map(Call::sql)
                    .filter(sql -> sql.contains(text))
                    .findFirst()
                    .orElseThrow();
        }

        private String listSqlContaining(String text) {
            return calls.stream()
                    .map(Call::sql)
                    .filter(sql -> sql.contains("FROM request_post rp"))
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

    private static final class RecordingPointManager implements PointManager {
        private final List<Integer> frozenPoints = new ArrayList<>();

        @Override
        public void freezeForRequest(Long memberId, Integer points, Long requestId) {
            frozenPoints.add(points);
        }

        @Override
        public void refundRequest(Long requestId) {
        }

        @Override
        public void transferReward(Long requestId, Long winnerMemberId) {
        }

        @Override
        public Long recordEvent(Long memberId, String type, Integer amount, Integer afterBalance, String scene, Long relatedTargetId, String remark) {
            return 1L;
        }

        private List<Integer> frozenPoints() {
            return frozenPoints;
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
