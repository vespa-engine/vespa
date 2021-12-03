// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.subscription;

import com.yahoo.config.ConfigInstance;
import com.yahoo.config.ConfigurationRuntimeException;
import com.yahoo.config.subscription.impl.ConfigSubscription;
import com.yahoo.config.subscription.impl.JrtConfigRequesters;
import com.yahoo.foo.AppConfig;
import com.yahoo.foo.SimpletypesConfig;
import com.yahoo.vespa.config.ConfigKey;
import com.yahoo.vespa.config.TimingValues;
import org.junit.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * @author hmusum
 * @author Ulf Lillengen
 */
public class ConfigSubscriptionTest {

    @Test
    public void testEquals() {
        ConfigSubscriber sub = new ConfigSubscriber();

        JrtConfigRequesters requesters = new JrtConfigRequesters();
        ConfigSubscription<SimpletypesConfig> a = createSubscription(requesters, "test");
        ConfigSubscription<SimpletypesConfig> b = createSubscription(requesters, "test");
        ConfigSubscription<SimpletypesConfig> c = createSubscription(requesters, "test2");
        assertEquals(b, a);
        assertEquals(a, a);
        assertEquals(b, b);
        assertEquals(c, c);
        assertNotEquals(c, a);
        assertNotEquals(c, b);

        ConfigSet configSet = new ConfigSet();
        AppConfig.Builder a0builder = new AppConfig.Builder().message("A message, 0").times(88);
        configSet.addBuilder("app/0", a0builder);
        AppConfig.Builder a1builder = new AppConfig.Builder().message("A message, 1").times(89);
        configSet.addBuilder("app/1", a1builder);


        ConfigSubscription<AppConfig> c1 = ConfigSubscription.get(
                new ConfigKey<>(AppConfig.class, "app/0"),
                requesters,
                configSet,
                new TimingValues());
        ConfigSubscription<AppConfig> c2 = ConfigSubscription.get(
                new ConfigKey<>(AppConfig.class, "app/1"),
                requesters,
                configSet,
                new TimingValues());

        assertEquals(c1, c1);
        assertNotEquals(c1, c2);
        sub.close();
    }

    @Test
    public void testSubscribeInterface() {
        ConfigSubscriber sub = new ConfigSubscriber();
        ConfigHandle<SimpletypesConfig> handle = sub.subscribe(SimpletypesConfig.class, "raw:boolval true", 10000);
        assertNotNull(handle);
        assertTrue(sub.nextConfig(false));
        assertTrue(handle.getConfig().boolval());
        sub.close();
    }

    // Test that exception is thrown if subscribe fails and that subscription is closed if we close the subscriber
    @Test
    public void testSubscribeWithException() {
        TestConfigSubscriber sub = new TestConfigSubscriber();
        ConfigSourceSet configSourceSet = new ConfigSourceSet(Collections.singletonList("tcp/localhost:99999"));
        try {
            sub.subscribe(SimpletypesConfig.class, "configid", configSourceSet, new TimingValues().setSubscribeTimeout(100));
            fail();
        } catch (ConfigurationRuntimeException e) {
            sub.close();
            assertTrue(sub.getSubscriptionHandles().get(0).subscription().isClosed());
        }
    }

    private ConfigSubscription<SimpletypesConfig> createSubscription(JrtConfigRequesters requesters, String configId) {
        return ConfigSubscription.get(new ConfigKey<>(SimpletypesConfig.class, configId),
                                      requesters, new RawSource("boolval true"), new TimingValues());
    }

    private static class TestConfigSubscriber extends ConfigSubscriber {
        List<ConfigHandle<? extends ConfigInstance>> getSubscriptionHandles() {
            return subscriptionHandles;
        }
    }

}
