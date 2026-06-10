package com.resourcesharing.forum.service.point;

import com.resourcesharing.forum.service.support.TxSupport;
import com.resourcesharing.forum.service.support.ValueSupport;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class PointRuleService {
    public static final String DAILY_LOGIN = "point.daily_login";
    public static final String RESOURCE_FAVORITED = "point.resource_favorited";
    public static final String RESOURCE_LIKED = "point.resource_liked";
    public static final String RESOURCE_APPROVED = "point.resource_approved";
    public static final String RESOURCE_DOWNLOADED = "point.resource_downloaded";
    public static final String REQUEST_ACCEPTED = "point.request_accepted";
    public static final String VIOLATION_PENALTY = "point.violation_penalty";
    public static final String RESOURCE_OFFLINE_PENALTY = "point.resource_offline_penalty";
    public static final String COMMENT_DELETE_PENALTY = "point.comment_delete_penalty";
    public static final String RESOURCE_DAILY_PUBLISH_LIMIT = "resource.daily_publish_limit";
    public static final String REQUEST_DAILY_PUBLISH_LIMIT = "request.daily_publish_limit";

    private final TxSupport txSupport;
    private final ValueSupport values;

    public PointRuleService(TxSupport txSupport, ValueSupport values) {
        this.txSupport = txSupport;
        this.values = values;
    }

    public int dailyLoginPoints() {
        return points(DAILY_LOGIN, 10);
    }

    public int resourceFavoritedPoints() {
        return points(RESOURCE_FAVORITED, 5);
    }

    public int resourceLikedPoints() {
        return points(RESOURCE_LIKED, 3);
    }

    public int resourceApprovedPoints() {
        return points(RESOURCE_APPROVED, 10);
    }

    public int resourceDownloadedPoints() {
        return points(RESOURCE_DOWNLOADED, 5);
    }

    public int requestAcceptedBonus() {
        return points(REQUEST_ACCEPTED, 10);
    }

    public int violationPenalty() {
        return points(VIOLATION_PENALTY, 10);
    }

    public int resourceOfflinePenalty() {
        return points(RESOURCE_OFFLINE_PENALTY, 20);
    }

    public int commentDeletePenalty() {
        return points(COMMENT_DELETE_PENALTY, 5);
    }

    public int resourceDailyPublishLimit() {
        return points(RESOURCE_DAILY_PUBLISH_LIMIT, 5);
    }

    public int requestDailyPublishLimit() {
        return points(REQUEST_DAILY_PUBLISH_LIMIT, 5);
    }

    public int points(String key, int fallback) {
        JdbcTemplate jdbc = txSupport.jdbc();
        if (jdbc == null) {
            return fallback;
        }
        try {
            String value = jdbc.queryForObject("""
                    SELECT config_value
                    FROM system_config
                    WHERE config_key = ?
                      AND is_enabled = 1
                      AND deleted_at IS NULL
                    LIMIT 1
                    """, String.class, key);
            return Math.max(0, values.intValue(value, fallback));
        } catch (DataAccessException ignored) {
            return fallback;
        }
    }

    public List<Map<String, Object>> rules() {
        return List.of(
                rule(DAILY_LOGIN, "每日登录", "+" + dailyLoginPoints(), "同一会员每日首次登录奖励一次。"),
                rule(RESOURCE_FAVORITED, "资源被收藏", "+" + resourceFavoritedPoints(), "资源首次被他人收藏时奖励发布者。"),
                rule(RESOURCE_LIKED, "资源被点赞", "+" + resourceLikedPoints(), "资源首次被他人点赞时奖励发布者。"),
                rule(RESOURCE_APPROVED, "资源审核通过", "+" + resourceApprovedPoints(), "资源通过后台审核后奖励发布者。"),
                rule(RESOURCE_DOWNLOADED, "资源被下载", "+" + resourceDownloadedPoints(), "他人首次成功下载资源时奖励发布者。"),
                rule(REQUEST_ACCEPTED, "回答被采纳", "+" + requestAcceptedBonus(), "求资源回答被采纳后给予回答者平台奖励。"),
                rule(VIOLATION_PENALTY, "违规成立", "-" + violationPenalty(), "管理员确认举报或违规处理后扣减违规方积分。")
        );
    }

    private Map<String, Object> rule(String key, String action, String points, String note) {
        return values.map(
                "key", key,
                "action", action,
                "points", points,
                "note", note
        );
    }
}
