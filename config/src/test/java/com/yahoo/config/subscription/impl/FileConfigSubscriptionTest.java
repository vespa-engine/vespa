// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.subscription.impl;

import com.yahoo.foo.SimpletypesConfig;
import com.yahoo.foo.TestReferenceConfig;
import com.yahoo.config.subscription.ConfigSubscriber;
import com.yahoo.config.subscription.DirSource;
import com.yahoo.config.subscription.FileSource;
import com.yahoo.vespa.config.ConfigKey;
import com.yahoo.vespa.config.TimingValues;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

/**
 * @author lulf
 * @since 5.1.7
 */
public class FileConfigSubscriptionTest {
    private File TEST_TYPES_FILE;

    @Before
    public void setUp() throws IOException {
       TEST_TYPES_FILE = File.createTempFile("fooconfig", ".cfg");
    }

    private void writeConfig(String field, String value) throws IOException {
        FileWriter writer = new FileWriter(TEST_TYPES_FILE);
        writer.write(field + " " + value);
        writer.close();
    }

    @Test
    public void require_that_new_config_is_detected_in_time() throws IOException, InterruptedException {
        writeConfig("intval", "23");
        ConfigSubscriber subscriber = new ConfigSubscriber(new FileSource(TEST_TYPES_FILE));
        ConfigSubscription<SimpletypesConfig> sub = new FileConfigSubscription<>(
                new ConfigKey<>(SimpletypesConfig.class, ""),
                subscriber,
                TEST_TYPES_FILE);
        assertTrue(sub.nextConfig(1000));
        assertThat(sub.getConfigState().getConfig().intval(), is(23));
        Thread.sleep(1000);
        writeConfig("intval", "33");
        assertTrue(sub.nextConfig(1000));
        assertThat(sub.getConfigState().getConfig().intval(), is(33));
    }

    @Test
    public void require_that_new_config_is_detected_on_reload() throws IOException {
        writeConfig("intval", "23");
        ConfigSubscriber subscriber = new ConfigSubscriber(new FileSource(TEST_TYPES_FILE));
        ConfigSubscription<SimpletypesConfig> sub = new FileConfigSubscription<>(
                new ConfigKey<>(SimpletypesConfig.class, ""),
                subscriber,
                TEST_TYPES_FILE);
        assertTrue(sub.nextConfig(1000));
        assertThat(sub.getConfigState().getConfig().intval(), is(23));
        writeConfig("intval", "33");
        sub.reload(1);
        assertTrue(sub.nextConfig(1000));
        ConfigSubscription.ConfigState<SimpletypesConfig> configState = sub.getConfigState();
        assertThat(configState.getConfig().intval(), is(33));
        assertTrue(configState.isConfigChanged());
        assertTrue(configState.isGenerationChanged());

        assertTrue(sub.isConfigChangedAndReset(7L));
        assertSame(configState, sub.getConfigState());
        assertTrue(configState.isConfigChanged());
        assertTrue(configState.isGenerationChanged());
        assertTrue(sub.isConfigChangedAndReset(1L));
        assertNotSame(configState, sub.getConfigState());
        configState = sub.getConfigState();
        assertFalse(configState.isConfigChanged());
        assertFalse(configState.isGenerationChanged());

        sub.reload(2);
        assertTrue(sub.nextConfig(1000));
        configState = sub.getConfigState();
        assertThat(configState.getConfig().intval(), is(33));
        assertFalse(configState.isConfigChanged());
        assertTrue(configState.isGenerationChanged());

        assertFalse(sub.isConfigChangedAndReset(2L));
        assertNotSame(configState, sub.getConfigState());
        configState = sub.getConfigState();
        assertFalse(configState.isConfigChanged());
        assertFalse(configState.isGenerationChanged());
    }

    @Test
    public void require_that_dir_config_id_reference_is_not_changed() {
        final String cfgDir = "src/test/resources/configs/foo";
        final String cfgId = "dir:" + cfgDir;
        final ConfigKey<TestReferenceConfig> key = new ConfigKey<>(TestReferenceConfig.class, cfgId);
        ConfigSubscriber subscriber = new ConfigSubscriber();
        ConfigSubscription<TestReferenceConfig> sub = ConfigSubscription.get(key, subscriber, new DirSource(new File(cfgDir)), new TimingValues());
        assertTrue(sub.nextConfig(1000));
        assertThat(sub.getConfigState().getConfig().configId(), is(cfgId));
    }

    @Test(expected = IllegalArgumentException.class)
    public void require_that_bad_file_throws_exception() throws IOException {
        // A little trick to ensure that we can create the subscriber, but that we get an error when reading.
        writeConfig("intval", "23");
        ConfigSubscriber subscriber = new ConfigSubscriber(new FileSource(TEST_TYPES_FILE));
        ConfigSubscription<SimpletypesConfig> sub = new FileConfigSubscription<>(
                new ConfigKey<>(SimpletypesConfig.class, ""),
                subscriber,
                TEST_TYPES_FILE);
        sub.reload(1);
        Files.delete(TEST_TYPES_FILE.toPath()); // delete file so the below statement throws exception
        sub.nextConfig(0);
    }
}
