// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.flags;

import com.yahoo.vespa.test.file.TestFileSystem;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

public class FileFlagSourceTest {
    private final FileSystem fileSystem = TestFileSystem.create();
    private final FileFlagSource source = new FileFlagSource(fileSystem);
    private final FlagId id = new FlagId("foo");

    @Test
    public void testFeatureLikeFlags() throws IOException {
        FeatureFlag featureFlag = new FeatureFlag(id, source);
        FeatureFlag byDefaultTrue = featureFlag.defaultToTrue();

        assertFalse(featureFlag.value());
        assertTrue(byDefaultTrue.value());

        writeFlag(id.toString(), "True\n");

        assertTrue(featureFlag.value());
        assertTrue(byDefaultTrue.value());

        writeFlag(id.toString(), "False\n");

        assertFalse(featureFlag.value());
        assertFalse(byDefaultTrue.value());
    }

    @Test
    public void testIntegerLikeFlags() throws IOException {
        StringFlag stringFlag = new StringFlag(id, "default", source);
        IntFlag intFlag = new IntFlag(id, -1, source);
        LongFlag longFlag = new LongFlag(id, -2L, source);

        assertFalse(source.getString(id).isPresent());
        assertEquals("default", stringFlag.value());
        assertEquals(-1, intFlag.value());
        assertEquals(-2L, longFlag.value());

        writeFlag(id.toString(), "1\n");

        assertTrue(source.getString(id).isPresent());
        assertEquals("1\n", stringFlag.value());
        assertEquals(1, intFlag.value());
        assertEquals(1L, longFlag.value());
    }

    @Test
    public void parseFailure() throws IOException {
        FeatureFlag featureFlag = new FeatureFlag(id, source);
        writeFlag(featureFlag.id().toString(), "garbage");

        try {
            featureFlag.value();
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage(), containsString("garbage"));
        }
    }

    private void writeFlag(String flagId, String value) throws IOException {
        Path featurePath = fileSystem.getPath(FileFlagSource.FLAGS_DIRECTORY).resolve(flagId);
        Files.createDirectories(featurePath.getParent());
        Files.write(featurePath, value.getBytes());
    }
}