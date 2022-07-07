// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.subscription;

import com.yahoo.foo.AppConfig;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Unit test for the {@link ConfigGetter}.
 *
 * @author gjoranv
 */
@SuppressWarnings("deprecation")
public class ConfigGetterTest {
    private final ConfigSourceSet sourceSet = new ConfigSourceSet("config-getter-test");

    @Test
    public void testGetConfig() {
        int times = 11;
        String message = "testGetConfig";
        String a0 = "a0";
        String configId = "raw:times " + times + "\nmessage " + message + "\na[1]\na[0].name " + a0;

        ConfigGetter<AppConfig> getter = new ConfigGetter<>(AppConfig.class);
        AppConfig config = getter.getConfig(configId);
        assertEquals(times, config.times());
        assertEquals(message, config.message());
        assertEquals(1, config.a().size());
        assertEquals(a0, config.a(0).name());

        AppService service = new AppService(configId, sourceSet);
        AppConfig serviceConfig = service.getConfig();
        assertTrue(service.isConfigured());
        assertEquals(config, serviceConfig);

        service.cancelSubscription();
    }

    @Test
    public void testGetTwice() {
        ConfigGetter<AppConfig> getter = new ConfigGetter<>(AppConfig.class);
        AppConfig config = getter.getConfig("raw:message \"one\"");
        assertEquals("one", config.message());
        config = getter.getConfig("raw:message \"two\"");
        assertEquals("two", config.message());
    }

    @Test
    public void testGetFromFile() {
        ConfigGetter<AppConfig> getter = new ConfigGetter<>(AppConfig.class);
        AppConfig config = getter.getConfig("file:src/test/resources/configs/foo/app.cfg");
        verifyFooValues(config);
    }

    @Test
    public void testGetFromDir() {
        ConfigGetter<AppConfig> getter = new ConfigGetter<>(AppConfig.class);
        AppConfig config = getter.getConfig("dir:src/test/resources/configs/foo/");
        verifyFooValues(config);
    }

    private void verifyFooValues(AppConfig config) {
        assertEquals("msg1", config.message());
        assertEquals(3, config.times());
        assertEquals("a0", config.a(0).name());
        assertEquals("a1", config.a(1).name());
        assertEquals("a2", config.a(2).name());
    }

    @Test
    public void testsStaticGetConfig() {
        int times = 11;
        String message = "testGetConfig";
        String a0 = "a0";
        String configId = "raw:times " + times + "\nmessage " + message + "\na[1]\na[0].name " + a0;

        AppConfig config = ConfigGetter.getConfig(AppConfig.class, configId);
        assertEquals(times, config.times());
        assertEquals(message, config.message());
        assertEquals(1, config.a().size());
        assertEquals(a0, config.a(0).name());

        AppService service = new AppService(configId, sourceSet);
        AppConfig serviceConfig = service.getConfig();
        assertTrue(service.isConfigured());
        assertEquals(config, serviceConfig);

        service.cancelSubscription();
    }
}
