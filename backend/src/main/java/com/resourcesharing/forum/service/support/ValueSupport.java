package com.resourcesharing.forum.service.support;

import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class ValueSupport {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ISO_LOCAL_DATE;

    public int page(Map<String, String> params) {
        return Math.max(1, intValue(params == null ? null : params.get("page"), 1));
    }

    public int size(Map<String, String> params) {
        String requested = params == null ? null : firstNonBlank(params.get("size"), params.get("pageSize"));
        return Math.max(1, Math.min(100, intValue(requested, 20)));
    }

    public Long key(KeyHolder keyHolder) {
        Number key = keyHolder.getKey();
        return key == null ? 0L : key.longValue();
    }

    public Object firstPresent(Map<String, Object> request, String... keys) {
        if (request == null) {
            return null;
        }
        for (String key : keys) {
            if (request.containsKey(key) && request.get(key) != null) {
                return request.get(key);
            }
        }
        return null;
    }

    public String nullable(Map<String, Object> request, String key) {
        if (request == null || request.get(key) == null || String.valueOf(request.get(key)).isBlank()) {
            return null;
        }
        return String.valueOf(request.get(key));
    }

    public String value(Map<String, Object> request, String key, String fallback) {
        Object value = request == null ? null : request.get(key);
        return value == null ? fallback : String.valueOf(value);
    }

    public Long number(Object value, Long fallback) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null || String.valueOf(value).isBlank()) {
            return fallback;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    public int intValue(String value, int fallback) {
        try {
            return value == null || value.isBlank() ? fallback : Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    public Long longValue(String value, Long fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    public String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    public String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public List<String> splitTags(String tagsText) {
        if (tagsText == null || tagsText.isBlank()) {
            return List.of();
        }
        return Arrays.stream(tagsText.split("[,\\uFF0C]"))
                .map(String::trim)
                .filter(tag -> !tag.isBlank())
                .limit(5)
                .collect(Collectors.toList());
    }

    public String today() {
        return LocalDate.now().format(DATE);
    }

    public String date(LocalDateTime time) {
        return time == null ? today() : time.toLocalDate().format(DATE);
    }

    public String stringId(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    public Map<String, Object> map(Object... pairs) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            result.put(String.valueOf(pairs[i]), pairs[i + 1]);
        }
        return result;
    }
}
