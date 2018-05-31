// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.subscription;

import static org.junit.Assert.*;

import com.yahoo.foo.AppConfig;
import com.yahoo.foo.SimpletypesConfig;
import com.yahoo.foo.StringConfig;
import com.yahoo.config.subscription.impl.ConfigSubscription;
import com.yahoo.vespa.config.ConfigKey;
import com.yahoo.vespa.config.TimingValues;
import org.junit.Test;

public class ConfigSetSubscriptionTest {

    @Test
    public void testConfigSubscription() {
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

    @Test(expected = IllegalArgumentException.class)
    public void testUnknownKey() {
        ConfigSubscriber subscriber = new ConfigSubscriber();
        ConfigSet configSet = new ConfigSet();
        AppConfig.Builder a0builder = new AppConfig.Builder().message("A message, 0").times(88);
        configSet.addBuilder("app/0", a0builder);

        ConfigSubscription.get(
                new ConfigKey<>(SimpletypesConfig.class, "simpletypes/1"),
                subscriber,
                configSet,
                new TimingValues());
    }

    @Test
    public void testConfigSetBasic() {
        ConfigSet myConfigs = new ConfigSet();
        AppConfig.Builder a0builder = new AppConfig.Builder().message("A message, 0").times(88);
        AppConfig.Builder a1builder = new AppConfig.Builder().message("A message, 1").times(89);
        StringConfig.Builder barBuilder = new StringConfig.Builder().stringVal("StringVal");
        myConfigs.addBuilder("app/0", a0builder);
        myConfigs.addBuilder("app/1", a1builder);
        myConfigs.addBuilder("bar", barBuilder);
        ConfigSubscriber subscriber = new ConfigSubscriber(myConfigs);
        ConfigHandle<AppConfig> hA0 = subscriber.subscribe(AppConfig.class, "app/0");
        ConfigHandle<AppConfig> hA1 = subscriber.subscribe(AppConfig.class, "app/1");
        ConfigHandle<StringConfig> hS = subscriber.subscribe(StringConfig.class, "bar");

        assertTrue(subscriber.nextConfig(0));
        assertTrue(hA0.isChanged());
        assertTrue(hA1.isChanged());
        assertTrue(hS.isChanged());

        assertEquals(hA0.getConfig().message(), "A message, 0");
        assertEquals(hA1.getConfig().message(), "A message, 1");
        assertEquals(hA0.getConfig().times(), 88);
        assertEquals(hA1.getConfig().times(), 89);

        assertFalse(subscriber.nextConfig(10));
        assertFalse(hA0.isChanged());
        assertFalse(hA1.isChanged());
        assertFalse(hS.isChanged());
        assertEquals(hA0.getConfig().message(), "A message, 0");
        assertEquals(hA1.getConfig().message(), "A message, 1");
        assertEquals(hA0.getConfig().times(), 88);
        assertEquals(hA1.getConfig().times(), 89);
        assertEquals(hS.getConfig().stringVal(), "StringVal");

        // Reconfigure all configs, generation should change
        a0builder.message("A new message, 0").times(880);
        a1builder.message("A new message, 1").times(890);
        barBuilder.stringVal("new StringVal");
        subscriber.reload(1);
        assertTrue(subscriber.nextConfig(0));
        assertTrue(hA0.isChanged());
        assertTrue(hA1.isChanged());
        assertTrue(hS.isChanged());

        assertEquals(hA0.getConfig().message(), "A new message, 0");
        assertEquals(hA1.getConfig().message(), "A new message, 1");
        assertEquals(hA0.getConfig().times(), 880);
        assertEquals(hA1.getConfig().times(), 890);
        assertEquals(hS.getConfig().stringVal(), "new StringVal");

        // Reconfigure only one
        a0builder.message("Another new message, 0").times(8800);
        subscriber.reload(2);
        assertTrue(subscriber.nextConfig(0));
        assertTrue(hA0.isChanged());
        assertFalse(hA1.isChanged());
        assertFalse(hS.isChanged());

        assertEquals(hA0.getConfig().message(), "Another new message, 0");
        assertEquals(hA1.getConfig().message(), "A new message, 1");
        assertEquals(hA0.getConfig().times(), 8800);
        assertEquals(hA1.getConfig().times(), 890);
        assertEquals(hS.getConfig().stringVal(), "new StringVal");

        //Reconfigure only one, and only one field on the builder
        a1builder.message("Yet another message, 1");
        subscriber.reload(3);
        assertTrue(subscriber.nextConfig(0));
        assertFalse(hA0.isChanged());
        assertTrue(hA1.isChanged());
        assertFalse(hS.isChanged());

        assertEquals(hA0.getConfig().message(), "Another new message, 0");
        assertEquals(hA1.getConfig().message(), "Yet another message, 1");
        assertEquals(hA0.getConfig().times(), 8800);
        assertEquals(hA1.getConfig().times(), 890);
        assertEquals(hS.getConfig().stringVal(), "new StringVal");
    }

    @Test
    public void requireThatWeGetLatestConfigWhenTwoUpdatesBeforeClientChecks() {
        ConfigSet myConfigs = new ConfigSet();
        AppConfig.Builder a0builder = new AppConfig.Builder().message("A message, 1");
        myConfigs.addBuilder("app/0", a0builder);
        ConfigSubscriber subscriber = new ConfigSubscriber(myConfigs);
        ConfigHandle<AppConfig> hA0 = subscriber.subscribe(AppConfig.class, "app/0");

        assertTrue(subscriber.nextConfig(0));
        assertTrue(hA0.isChanged());
        assertEquals(hA0.getConfig().message(), "A message, 1");

        assertFalse(subscriber.nextConfig(10));
        assertFalse(hA0.isChanged());
        assertEquals(hA0.getConfig().message(), "A message, 1");

        //Reconfigure two times in a row
        a0builder.message("A new message, 2");
        subscriber.reload(1);
        a0builder.message("An even newer message, 3");
        subscriber.reload(2);

        // Should pick up the last one
        assertTrue(subscriber.nextConfig(0));
        assertTrue(hA0.isChanged());
        assertEquals(hA0.getConfig().message(), "An even newer message, 3");
    }

}
