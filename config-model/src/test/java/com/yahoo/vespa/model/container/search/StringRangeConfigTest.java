// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.search;

import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.deploy.TestProperties;
import com.yahoo.config.model.test.MockRoot;
import com.yahoo.container.QrSearchersConfig;
import com.yahoo.vespa.model.container.ApplicationContainerCluster;
import com.yahoo.vespa.model.container.search.searchchain.SearchChains;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests that the string range feature flag reaches the query parser config of the container.
 *
 * @author boeker
 */
public class StringRangeConfigTest {

    @Test
    void string_ranges_are_off_by_default() {
        assertFalse(parserSettingsWithStringRanges(false).allowStringRanges());
    }

    @Test
    void string_ranges_are_turned_on_by_the_feature_flag() {
        assertTrue(parserSettingsWithStringRanges(true).allowStringRanges());
    }

    /** The string range setting must not displace the other parser settings, which share the same config struct. */
    @Test
    void string_ranges_do_not_displace_the_other_parser_settings() {
        assertTrue(parserSettingsWithStringRanges(true).keepSegmentAnds());
    }

    private static QrSearchersConfig.ParserSettings parserSettingsWithStringRanges(boolean allowStringRanges) {
        DeployState state = new DeployState.Builder()
                .properties(new TestProperties().allowStringRanges(allowStringRanges))
                .build();
        MockRoot root = new MockRoot("root", state);
        var cluster = new ApplicationContainerCluster(root, "container0", "container1", state);
        var search = new ContainerSearch(state, cluster, new SearchChains(cluster, "search-chain"));

        var builder = new QrSearchersConfig.Builder();
        search.getConfig(builder);
        return builder.build().parserSettings();
    }

}
