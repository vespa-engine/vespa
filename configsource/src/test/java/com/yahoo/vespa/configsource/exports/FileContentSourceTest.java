// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.configsource.exports;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class FileContentSourceTest {
    @Rule
    public final TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void basics() throws IOException {
        FileContentSource source = new FileContentSource();
        Path directory = folder.newFolder().toPath();
        Path path = directory.resolve("simple.json");
        JacksonDeserializer<SimpleJson> deserializer = new JacksonDeserializer<>(SimpleJson.class);
        FileContentSupplier<SimpleJson> supplier = source.newSupplier(path, deserializer);

        // when JSON file does not exist...

        Optional<SimpleJson> snapshot = supplier.getSnapshot();
        assertFalse(snapshot.isPresent());

        // write JSON file

        Files.write(path, "{\"field\": \"value\"}".getBytes(StandardCharsets.UTF_8));
        snapshot = supplier.getSnapshot();
        assertTrue(snapshot.isPresent());
        assertEquals("value", snapshot.get().field);

        // write garbage => unchanged

        Files.write(path, "{\"field\": \"val".getBytes(StandardCharsets.UTF_8));
        try {
            supplier.getSnapshot();
            fail();
        } catch (UncheckedIOException e) {
            // expected
        }

        // remove file

        Files.delete(path);
        snapshot = supplier.getSnapshot();
        assertFalse(snapshot.isPresent());
    }
}