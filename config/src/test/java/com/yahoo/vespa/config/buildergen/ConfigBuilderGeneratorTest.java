// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.buildergen;

import com.google.common.io.Files;
import com.yahoo.config.ConfigInstance;
import com.yahoo.config.codegen.ConfigGenerator;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Slime;
import com.yahoo.vespa.config.ConfigDefinitionKey;
import com.yahoo.vespa.config.ConfigPayload;
import com.yahoo.vespa.config.ConfigPayloadApplier;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.URISyntaxException;

import static junit.framework.TestCase.assertNotNull;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

/**
 * @author lulf
 * @since 5.1
 */
public class ConfigBuilderGeneratorTest {
    @Test
    public void require_that_custom_classes_can_be_generated() throws URISyntaxException, IOException, ClassNotFoundException, IllegalAccessException, InstantiationException, NoSuchMethodException, InvocationTargetException {
        String[] schema = new String[] {
                "namespace=foo.bar",
                "intval int",
                "stringval string"
        };
        File tempDir = Files.createTempDir();
        ConfigDefinitionKey key = new ConfigDefinitionKey("quux", "foo.bar");
        ConfigCompiler compiler = new LazyConfigCompiler(tempDir);
        ConfigInstance.Builder builder = compiler.compile(new ConfigDefinition(key.getName(), schema).generateClass()).newInstance();
        assertNotNull(builder);
        ConfigPayloadApplier<?> payloadApplier = new ConfigPayloadApplier<>(builder);
        Slime slime = new Slime();
        Cursor root = slime.setObject();
        root.setString("intval", "3");
        root.setString("stringval", "Hello, world");
        payloadApplier.applyPayload(new ConfigPayload(slime));
        String className = ConfigGenerator.createClassName(key.getName());
        ConfigInstance instance = (ConfigInstance) builder.getClass().getClassLoader().loadClass("com.yahoo." + key.getNamespace() + "." + className).getConstructor(new Class<?>[]{builder.getClass()}).newInstance(builder);
        assertNotNull(instance);
        assertThat(instance.toString(), is("intval 3\nstringval \"Hello, world\""));
    }
}
