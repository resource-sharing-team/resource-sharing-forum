package com.resourcesharing.forum.service.support;

import com.resourcesharing.forum.common.BusinessException;
import com.resourcesharing.forum.common.ErrorCode;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

@Service
public class ContentModerationService {
    private final TxSupport txSupport;

    public ContentModerationService(TxSupport txSupport) {
        this.txSupport = txSupport;
    }

    public void requireClean(String content) {
        if (content == null || content.isBlank()) {
            return;
        }
        JdbcTemplate jdbc = txSupport.jdbc();
        if (jdbc == null) {
            return;
        }
        List<Map<String, Object>> rules;
        try {
            rules = jdbc.queryForList("""
                    SELECT word, match_type
                    FROM sensitive_word
                    WHERE status = 'ENABLED' AND deleted_at IS NULL
                    """);
        } catch (DataAccessException ignored) {
            return;
        }
        if (containsSensitiveContent(content, rules)) {
            throw new BusinessException(ErrorCode.SENSITIVE_CONTENT, ErrorCode.SENSITIVE_CONTENT.message());
        }
    }

    private boolean containsSensitiveContent(String content, List<Map<String, Object>> rules) {
        String normalized = content.trim();
        String lowerContent = normalized.toLowerCase(Locale.ROOT);
        for (Map<String, Object> rule : rules) {
            String word = value(rule.get("word"));
            if (word.isBlank()) {
                continue;
            }
            String matchType = value(rule.get("match_type")).toUpperCase(Locale.ROOT);
            String lowerWord = word.toLowerCase(Locale.ROOT);
            if ("EXACT".equals(matchType) && lowerContent.equals(lowerWord)) {
                return true;
            }
            if ("REGEX".equals(matchType) && regexMatches(word, normalized)) {
                return true;
            }
            if (!"EXACT".equals(matchType) && !"REGEX".equals(matchType) && lowerContent.contains(lowerWord)) {
                return true;
            }
        }
        return false;
    }

    private boolean regexMatches(String pattern, String content) {
        try {
            return Pattern.compile(pattern, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE).matcher(content).find();
        } catch (PatternSyntaxException ignored) {
            return false;
        }
    }

    private String value(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
