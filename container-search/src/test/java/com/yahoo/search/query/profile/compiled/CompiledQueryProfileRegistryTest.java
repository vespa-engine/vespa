// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.profile.compiled;

import com.yahoo.search.query.profile.config.QueryProfilesConfig;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author gjoranv
 */
public class CompiledQueryProfileRegistryTest {

    @Test
    public void registry_can_be_created_from_config() {
        var config = new QueryProfilesConfig.Builder()
                .queryprofile(new QueryProfilesConfig.Queryprofile.Builder()
                                      .id("profile1")
                                      .property(new QueryProfilesConfig.Queryprofile.Property.Builder()
                                                        .name("hits")
                                                        .value("5")))
                .build();

        var registry = new CompiledQueryProfileRegistry(config);
        var profile1 = registry.findQueryProfile("profile1");
        assertEquals("5", profile1.get("hits"));
    }

}
