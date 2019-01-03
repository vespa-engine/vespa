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
        BooleanFlag booleanFlag = new UnboundBooleanFlag(id).bindTo(source);
        BooleanFlag byDefaultTrue = new UnboundBooleanFlag(id, true).bindTo(source);

        assertFalse(booleanFlag.value());
        assertTrue(byDefaultTrue.value());

        writeFlag(id.toString(), "true\n");

        assertTrue(booleanFlag.value());
        assertTrue(byDefaultTrue.value());

        writeFlag(id.toString(), "false\n");

        assertFalse(booleanFlag.value());
        assertFalse(byDefaultTrue.value());
    }

    @Test
    public void testIntegerLikeFlags() throws IOException {
        IntFlag intFlag = new UnboundIntFlag(id, -1).bindTo(source);
        LongFlag longFlag = new UnboundLongFlag(id, -2L).bindTo(source);

        assertFalse(fetch().isPresent());
        assertFalse(fetch().isPresent());
        assertEquals(-1, intFlag.value());
        assertEquals(-2L, longFlag.value());

        writeFlag(id.toString(), "1\n");

        assertTrue(fetch().isPresent());
        assertTrue(fetch().isPresent());
        assertEquals(1, intFlag.value());
        assertEquals(1L, longFlag.value());
    }

    @Test
    public void testStringFlag() throws IOException {
        StringFlag stringFlag = new UnboundStringFlag(id, "default").bindTo(source);
        assertFalse(fetch().isPresent());
        assertEquals("default", stringFlag.value());

        writeFlag(id.toString(), "\"1\\n\"\n");
        assertEquals("1\n", stringFlag.value());
    }

    @Test
    public void parseFailure() throws IOException {
        BooleanFlag booleanFlag = new UnboundBooleanFlag(id).bindTo(source);
        writeFlag(booleanFlag.id().toString(), "garbage");

        try {
            booleanFlag.value();
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