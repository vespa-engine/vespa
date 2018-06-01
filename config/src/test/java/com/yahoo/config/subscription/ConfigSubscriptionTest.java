// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.subscription;

import com.yahoo.config.ConfigInstance;
import com.yahoo.config.ConfigurationRuntimeException;
import com.yahoo.config.subscription.impl.GenericConfigHandle;
import com.yahoo.foo.SimpletypesConfig;
import com.yahoo.foo.AppConfig;
import com.yahoo.config.subscription.impl.ConfigSubscription;
import com.yahoo.vespa.config.ConfigKey;
import com.yahoo.vespa.config.TimingValues;

import org.junit.Ignore;
import org.junit.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

/**
 * @author hmusum
 * @author Ulf Lillengen
 */
public class ConfigSubscriptionTest {

    @Test
    public void testEquals() {
        ConfigSubscriber sub = new ConfigSubscriber();
        final String payload = "boolval true";
        ConfigSubscription<SimpletypesConfig> a = ConfigSubscription.get(new ConfigKey<>(SimpletypesConfig.class, "test"),
                sub, new RawSource(payload), new TimingValues());
        ConfigSubscription<SimpletypesConfig> b = ConfigSubscription.get(new ConfigKey<>(SimpletypesConfig.class, "test"),
                sub, new RawSource(payload), new TimingValues());
        ConfigSubscription<SimpletypesConfig> c = ConfigSubscription.get(new ConfigKey<>(SimpletypesConfig.class, "test2"),
                sub, new RawSource(payload), new TimingValues());
        assertEquals(b, a);
        assertEquals(a, a);
        assertEquals(b, b);
        assertEquals(c, c);
        assertNotEquals(c, a);
        assertNotEquals(c, b);

        ConfigSubscriber subscriber = new ConfigSubscriber();
        ConfigSet configSet = new ConfigSet();
        AppConfig.Builder a0builder = new AppConfig.Builder().message("A message, 0").times(88);
        configSet.addBuilder("app/0", a0builder);
        AppConfig.Builder a1builder = new AppConfig.Builder().message("A message, 1").times(89);
        configSet.addBuilder("app/1", a1builder);

        ConfigSubscription<AppConfig> c1 = ConfigSubscription.get(
                new ConfigKey<>(AppConfig.class, "app/0"),
                subscriber,
                configSet,
                new TimingValues());
        ConfigSubscription<AppConfig> c2 = ConfigSubscription.get(
                new ConfigKey<>(AppConfig.class, "app/1"),
                subscriber,
                configSet,
                new TimingValues());

        assertTrue(c1.equals(c1));
        assertFalse(c1.equals(c2));
    }

    @Test
    public void testSubscribeInterface() {
        ConfigSubscriber sub = new ConfigSubscriber();
        ConfigHandle<SimpletypesConfig> handle = sub.subscribe(SimpletypesConfig.class, "raw:boolval true", 10000);
        assertNotNull(handle);
        sub.nextConfig();
        assertTrue(handle.getConfig().boolval());
        //assertTrue(sub.getSource() instanceof RawSource);
    }

    // Test that subscription is closed and subscriptionHandles is empty if we get an exception
    // (only the last is possible to test right now).
    @Test
    @Ignore
    public void testSubscribeWithException() {
        TestConfigSubscriber sub = new TestConfigSubscriber();
        ConfigSourceSet configSourceSet = new ConfigSourceSet(Collections.singletonList("tcp/localhost:99999"));
        try {
            sub.subscribe(SimpletypesConfig.class, "configid", configSourceSet, new TimingValues().setSubscribeTimeout(100));
            fail();
        } catch (ConfigurationRuntimeException e) {
            assertEquals(0, sub.getSubscriptionHandles().size());
        }
    }

    private static class TestConfigSubscriber extends ConfigSubscriber {
        List<ConfigHandle<? extends ConfigInstance>> getSubscriptionHandles() {
            return subscriptionHandles;
        }
    }

}
