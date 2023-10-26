// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.api;

import com.yahoo.slime.SlimeUtils;
import org.junit.Test;


import java.math.BigDecimal;

import static org.junit.Assert.assertEquals;

/**
 * @author ogronnesby
 */
public class QuotaTest {

    @Test
    public void test_serialization_with_integers() {
        var json = "{\"budget\": 123}";
        var slime = SlimeUtils.jsonToSlime(json);
        var quota = Quota.fromSlime(slime.get());
        assertEquals((Integer) 123, quota.budget().get());
        assertEquals(123, quota.budgetAsDecimal().get().intValueExact());
    }

    @Test
    public void test_serialization_with_floats() {
        var json = "{\"budget\": 123.4}";
        var slime = SlimeUtils.jsonToSlime(json);
        var quota = Quota.fromSlime(slime.get());
        assertEquals((Integer) 123, quota.budget().get());
        assertEquals(123.4, quota.budgetAsDecimal().get().doubleValue(), 0.01);
    }

    @Test
    public void test_serialization_with_string() {
        var json = "{\"budget\": \"123.4\"}";
        var slime = SlimeUtils.jsonToSlime(json);
        var quota = Quota.fromSlime(slime.get());
        assertEquals((Integer) 123, quota.budget().get());
        assertEquals(new BigDecimal("123.4"), quota.budgetAsDecimal().get());
    }

    @Test
    public void test_serde() {
        var quota = Quota.unlimited().withBudget(BigDecimal.valueOf(23.5)).withClusterSize(11);
        var serialized = quota.toSlime();
        var deserialized = Quota.fromSlime(serialized.get());
        assertEquals(quota, deserialized);
    }
}
