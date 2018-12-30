// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.flags;

import com.yahoo.vespa.test.file.TestFileSystem;
import org.junit.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

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
        Flag<Boolean> featureFlag = new Flag<>(id, false, source, Flags.BOOLEAN_SERIALIZER);
        Flag<Boolean> byDefaultTrue = new Flag<>(id, true, source, Flags.BOOLEAN_SERIALIZER);

        assertFalse(featureFlag.value());
        assertTrue(byDefaultTrue.value());

        writeFlag(id.toString(), "true\n");

        assertTrue(featureFlag.value());
        assertTrue(byDefaultTrue.value());

        writeFlag(id.toString(), "false\n");

        assertFalse(featureFlag.value());
        assertFalse(byDefaultTrue.value());
    }

    @Test
    public void testIntegerLikeFlags() throws IOException {
        Flag<Integer> intFlag = new Flag<>(id, -1, source, Flags.INT_SERIALIZER);
        Flag<Long> longFlag = new Flag<>(id, -2L, source, Flags.LONG_SERIALIZER);

        assertFalse(fetch().isPresent());
        assertFalse(fetch().isPresent());
        assertEquals(-1, (int) intFlag.value());
        assertEquals(-2L, (long) longFlag.value());

        writeFlag(id.toString(), "1\n");

        assertTrue(fetch().isPresent());
        assertTrue(fetch().isPresent());
        assertEquals(1, (int) intFlag.value());
        assertEquals(1L, (long) longFlag.value());
    }

    @Test
    public void testStringFlag() throws IOException {
        Flag<String> stringFlag = new Flag<>(id, "default", source, Flags.STRING_SERIALIZER);
        assertFalse(fetch().isPresent());
        assertEquals("default", stringFlag.value());

        writeFlag(id.toString(), "\"1\\n\"\n");
        assertEquals("1\n", stringFlag.value());
    }

    @Test
    public void parseFailure() throws IOException {
        Flag<Boolean> featureFlag = new Flag<>(id, false, source, Flags.BOOLEAN_SERIALIZER);
        writeFlag(featureFlag.id().toString(), "garbage");

        try {
            featureFlag.value();
        } catch (UncheckedIOException e) {
            assertThat(e.getMessage(), containsString("garbage"));
        }
    }

    private Optional<RawFlag> fetch() {
        return source.fetch(id, new FetchVector());
    }

    private void writeFlag(String flagId, String value) throws IOException {
        Path featurePath = fileSystem.getPath(FileFlagSource.FLAGS_DIRECTORY).resolve(flagId);
        Files.createDirectories(featurePath.getParent());
        Files.write(featurePath, value.getBytes());
    }
}