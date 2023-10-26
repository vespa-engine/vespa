// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.standalone;

import com.yahoo.config.ConfigInstance;
import com.yahoo.container.ComponentsConfig;
import com.yahoo.container.di.config.ApplicationBundlesConfig;
import com.yahoo.container.di.config.PlatformBundlesConfig;
import com.yahoo.container.di.config.Subscriber;
import com.yahoo.vespa.config.ConfigKey;
import org.junit.Ignore;
import org.junit.Test;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static com.yahoo.container.standalone.StandaloneContainer.withContainerModel;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author Tony Vaagenes
 * @author ollivir
 */
public class StandaloneSubscriberTest {
    private static ConfigKey<ConfigInstance> platformBundlesKey = key("platform-bundles");
    private static ConfigKey<ConfigInstance> applicationBundlesKey = key("application-bundles");
    private static ConfigKey<ConfigInstance> componentsKey = key("components");

    private static ConfigKey<ConfigInstance> key(String name) {
        return new ConfigKey<>(name, "container", "container");
    }

    @Test
    @Ignore
    public void standalone_subscriber() throws Exception {
        withContainerModel("<container version=\"1.0\"></container>", root -> {
            Set<ConfigKey<ConfigInstance>> keys = new HashSet<>();
            keys.add(platformBundlesKey);
            keys.add(applicationBundlesKey);
            keys.add(componentsKey);
            Subscriber subscriber = new StandaloneSubscriberFactory(root).getSubscriber(keys, "standalone");
            Map<ConfigKey<ConfigInstance>, ConfigInstance> config = subscriber.config();
            assertEquals(2, config.size());

            PlatformBundlesConfig platformBundlesConfig = (PlatformBundlesConfig) config.get(platformBundlesKey);
            ApplicationBundlesConfig applicationBundlesConfig = (ApplicationBundlesConfig) config.get(applicationBundlesKey);
            ComponentsConfig componentsConfig = (ComponentsConfig) config.get(componentsKey);

            assertEquals(0, platformBundlesConfig.bundlePaths().size());
            assertEquals(0, applicationBundlesConfig.bundles().size());
            assertTrue(componentsConfig.components().size() > 10);
            return null;
        });
    }
}
