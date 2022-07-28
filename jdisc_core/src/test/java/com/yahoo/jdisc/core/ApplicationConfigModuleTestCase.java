// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.core;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.name.Names;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * @author Simon Thoresen Hult
 */
public class ApplicationConfigModuleTestCase {

    @Test
    void requireThatEntriesAreBoundWithLowerCaseKeys() {
        Map<String, String> config = new HashMap<>();
        config.put("foo_key", "foo");
        config.put("BAR_key", "bar");
        config.put("BAZ_KEY", "baz");

        Injector injector = Guice.createInjector(new ApplicationConfigModule(config));
        assertBinding(injector, "foo_key", "foo");
        assertBinding(injector, "bar_key", "bar");
        assertBinding(injector, "baz_key", "baz");
    }

    @Test
    void requireThatEntriesAreBoundWithUnmodifiedValue() {
        Map<String, String> config = new HashMap<>();
        config.put("foo", "foo_val");
        config.put("bar", "BAR_val");
        config.put("baz", "BAZ_VAL");

        Injector injector = Guice.createInjector(new ApplicationConfigModule(config));
        assertBinding(injector, "foo", "foo_val");
        assertBinding(injector, "bar", "BAR_val");
        assertBinding(injector, "baz", "BAZ_VAL");
    }

    @Test
    void requireThatUpperCaseKeysPrecedeLowerCaseKeys() {
        Map<String, String> config = new HashMap<>();
        config.put("foo", "lower-case");
        assertBinding(config, "foo", "lower-case");

        config.put("Foo", "mixed-case 1");
        assertBinding(config, "foo", "mixed-case 1");

        config.put("FOO", "upper-case");
        assertBinding(config, "foo", "upper-case");

        config.put("FOo", "mixed-case 2");
        assertBinding(config, "foo", "upper-case");
    }

    @Test
    void requireThatNullFileNameThrowsException() throws IOException {
        try {
            ApplicationConfigModule.newInstanceFromFile(null);
            fail();
        } catch (NullPointerException e) {

        }
    }

    @Test
    void requireThatFileNotFoundThrowsException() throws IOException {
        try {
            ApplicationConfigModule.newInstanceFromFile("/file/not/found");
            fail();
        } catch (FileNotFoundException e) {

        }
    }

    @Test
    void requireThatPropertieFilesCanBeRead() throws IOException {
        Properties props = new Properties();
        props.put("foo_key", "foo_val");

        File file = File.createTempFile("config-", ".properties");
        file.deleteOnExit();
        FileOutputStream out = new FileOutputStream(file);
        props.store(out, null);
        out.close();

        assertBinding(ApplicationConfigModule.newInstanceFromFile(file.getAbsolutePath()), "foo_key", "foo_val");
    }

    private static void assertBinding(Map<String, String> config, String stringName, String expected) {
        assertBinding(new ApplicationConfigModule(config), stringName, expected);
    }

    private static void assertBinding(Module module, String stringName, String expected) {
        assertBinding(Guice.createInjector(module), stringName, expected);
    }

    private static void assertBinding(Injector injector, String stringName, String expected) {
        assertEquals(expected, injector.getInstance(Key.get(String.class, Names.named(stringName))));
    }
}
