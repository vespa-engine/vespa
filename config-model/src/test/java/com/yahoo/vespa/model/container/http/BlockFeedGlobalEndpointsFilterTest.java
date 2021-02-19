// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.model.container.http;

import com.yahoo.config.model.api.ContainerEndpoint;
import com.yahoo.jdisc.http.filter.security.rule.RuleBasedFilterConfig;
import org.hamcrest.Matchers;
import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

public class BlockFeedGlobalEndpointsFilterTest {

    @Test
    public void setup_blocking_rule_when_endpoints_is_non_empty() {
        var endpoints = Set.of(new ContainerEndpoint("default", List.of("foo", "bar")));
        var filter = new BlockFeedGlobalEndpointsFilter(endpoints, true);
        var config = getConfig(filter);
        assertEquals(1, config.rule().size());
        var rule = config.rule().get(0);
        assertThat(rule.hostNames(), Matchers.containsInAnyOrder("foo", "bar"));
        assertEquals(rule.action(), RuleBasedFilterConfig.Rule.Action.Enum.BLOCK);
    }

    @Test
    public void does_not_setup_blocking_rule_when_endpoints_empty() {
        var filter = new BlockFeedGlobalEndpointsFilter(Collections.emptySet(), true);
        var config = getConfig(filter);
        assertEquals(0, config.rule().size());
    }

    private RuleBasedFilterConfig getConfig(BlockFeedGlobalEndpointsFilter filter) {
        var configBuilder = new RuleBasedFilterConfig.Builder();
        filter.getConfig(configBuilder);
        return configBuilder.build();
    }
}
