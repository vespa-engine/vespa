// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.standalone;

import com.yahoo.config.ConfigInstance;
import com.yahoo.container.BundlesConfig;
import com.yahoo.container.ComponentsConfig;
import com.yahoo.container.di.config.Subscriber;
import com.yahoo.vespa.config.ConfigKey;
import org.junit.Ignore;
import org.junit.Test;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static com.yahoo.container.standalone.StandaloneContainer.withContainerModel;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.number.OrderingComparison.greaterThan;
import static org.junit.Assert.assertThat;

/**
 * @author Tony Vaagenes
 * @author ollivir
 */
public class StandaloneSubscriberTest {
    private static ConfigKey<ConfigInstance> bundlesKey = key("bundles");
    private static ConfigKey<ConfigInstance> componentsKey = key("components");

    private static ConfigKey<ConfigInstance> key(String name) {
        return new ConfigKey<>(name, "container", "container");
    }

    @Test
    @Ignore
    public void standalone_subscriber() throws Exception {
        withContainerModel("<container version=\"1.0\"></container>", root -> {
            Set<ConfigKey<ConfigInstance>> keys = new HashSet<>();
            keys.add(bundlesKey);
            keys.add(componentsKey);
            Subscriber subscriber = new StandaloneSubscriberFactory(root).getSubscriber(keys);
            Map<ConfigKey<ConfigInstance>, ConfigInstance> config = subscriber.config();
            assertThat(config.size(), is(2));

            BundlesConfig bundlesConfig = (BundlesConfig) config.get(bundlesKey);
            ComponentsConfig componentsConfig = (ComponentsConfig) config.get(componentsKey);

            assertThat(bundlesConfig.bundle().size(), is(0));
            assertThat(componentsConfig.components().size(), greaterThan(10));
            return null;
        });
    }
}
