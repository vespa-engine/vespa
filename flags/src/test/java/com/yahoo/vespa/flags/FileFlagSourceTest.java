// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.flags;

import com.yahoo.vespa.test.file.TestFileSystem;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class FileFlagSourceTest {
    private final FileSystem fileSystem = TestFileSystem.create();
    private final FileFlagSource source = new FileFlagSource(fileSystem);

    @Test
    public void absentThenSet() throws IOException {
        FlagId id = new FlagId("foo");
        FeatureFlag featureFlag = new FeatureFlag(id, source);
        StringFlag stringFlag = new StringFlag(id, "default", source);
        OptionalStringFlag optionalStringFlag = new OptionalStringFlag(id, source);
        IntFlag intFlag = new IntFlag(id, -1, source);
        LongFlag longFlag = new LongFlag(id, -2L, source);

        assertFalse(source.hasFeature(id));
        assertFalse(source.getString(id).isPresent());
        assertFalse(featureFlag.isSet());
        assertEquals("default", stringFlag.value());
        assertFalse(optionalStringFlag.value().isPresent());
        assertEquals(-1, intFlag.value());
        assertEquals(-2L, longFlag.value());

        Path featurePath = fileSystem.getPath(FileFlagSource.FLAGS_DIRECTORY).resolve(id.toString());
        Files.createDirectories(featurePath.getParent());
        Files.write(featurePath, "1\n".getBytes());

        assertTrue(source.hasFeature(id));
        assertTrue(source.getString(id).isPresent());
        assertTrue(featureFlag.isSet());
        assertEquals("1\n", stringFlag.value());
        assertEquals("1\n", optionalStringFlag.value().get());
        assertEquals(1, intFlag.value());
        assertEquals(1L, longFlag.value());
    }
}