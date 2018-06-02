// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.subscription;

import com.yahoo.foo.AppConfig;

import org.junit.Test;

import java.io.File;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

/**
 * Unit test for the {@link ConfigGetter}.
 *
 * @author gjoranv
 */
public class ConfigGetterTest {
    private ConfigSourceSet sourceSet = new ConfigSourceSet("config-getter-test");

    @Test
    public void testGetConfig() {
        int times = 11;
        String message = "testGetConfig";
        String a0 = "a0";
        String configId = "raw:times " + times + "\nmessage " + message + "\na[1]\na[0].name " + a0;

        ConfigGetter<AppConfig> getter = new ConfigGetter<>(AppConfig.class);
        AppConfig config = getter.getConfig(configId);
        assertThat(config.times(), is(times));
        assertThat(config.message(), is(message));
        assertThat(config.a().size(), is(1));
        assertThat(config.a(0).name(), is(a0));

        AppService service = new AppService(configId, sourceSet);
        AppConfig serviceConfig = service.getConfig();
        assertTrue(service.isConfigured());
        assertThat(config, is(serviceConfig));
    }

    @Test
    public void testGetFromRawSource() {
        ConfigGetter<AppConfig> getter = new ConfigGetter<>(new RawSource("message \"one\""), AppConfig.class);
        AppConfig config = getter.getConfig("test");
        assertThat(config.message(), is("one"));
    }

    @Test
    public void testGetTwice() {
        ConfigGetter<AppConfig> getter = new ConfigGetter<>(AppConfig.class);
        AppConfig config = getter.getConfig("raw:message \"one\"");
        assertThat(config.message(), is("one"));
        config = getter.getConfig("raw:message \"two\"");
        assertThat(config.message(), is("two"));
    }

    @Test
    public void testGetFromFile() {
        ConfigGetter<AppConfig> getter = new ConfigGetter<>(AppConfig.class);
        AppConfig config = getter.getConfig("file:src/test/resources/configs/foo/app.cfg");
        verifyFooValues(config);
    }

     @Test
    public void testGetFromFileSource() {
        ConfigGetter<AppConfig> getter = new ConfigGetter<>(new FileSource(new File("src/test/resources/configs/foo/app.cfg")), AppConfig.class);
        AppConfig config = getter.getConfig("test");
        verifyFooValues(config);
    }

    @Test
    public void testGetFromDir() {
        ConfigGetter<AppConfig> getter = new ConfigGetter<>(AppConfig.class);
        AppConfig config = getter.getConfig("dir:src/test/resources/configs/foo/");
        verifyFooValues(config);
    }

    @Test
    public void testGetFromDirSource() {
        AppConfig config = ConfigGetter.getConfig(AppConfig.class, "test", new DirSource(new File("src/test/resources/configs/foo/")));
        verifyFooValues(config);
    }

    private void verifyFooValues(AppConfig config) {
        assertThat(config.message(), is("msg1"));
        assertThat(config.times(), is(3));
        assertThat(config.a(0).name(), is("a0"));
        assertThat(config.a(1).name(), is("a1"));
        assertThat(config.a(2).name(), is("a2"));
    }

    @Test
    public void testsStaticGetConfig() {
        int times = 11;
        String message = "testGetConfig";
        String a0 = "a0";
        String configId = "raw:times " + times + "\nmessage " + message + "\na[1]\na[0].name " + a0;

        AppConfig config = ConfigGetter.getConfig(AppConfig.class, configId);
        assertThat(config.times(), is(times));
        assertThat(config.message(), is(message));
        assertThat(config.a().size(), is(1));
        assertThat(config.a(0).name(), is(a0));

        AppService service = new AppService(configId, sourceSet);
        AppConfig serviceConfig = service.getConfig();
        assertTrue(service.isConfigured());
        assertThat(config, is(serviceConfig));
    }
}
