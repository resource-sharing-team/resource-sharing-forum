package com.resourcesharing.forum;

import com.resourcesharing.forum.service.support.ValueSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ValueSupportTest {
    @Test
    void longValueAllowsNullFallbackForOptionalQueryParameters() {
        ValueSupport values = new ValueSupport();

        assertThat(values.longValue(null, null)).isNull();
        assertThat(values.longValue("", null)).isNull();
        assertThat(values.longValue("bad", null)).isNull();
        assertThat(values.longValue("42", null)).isEqualTo(42L);
    }
}
