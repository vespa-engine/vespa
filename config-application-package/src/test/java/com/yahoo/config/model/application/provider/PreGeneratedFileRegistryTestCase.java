// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.application.provider;

import com.yahoo.config.application.api.FileRegistry;
import org.junit.Test;

import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import static org.junit.Assert.assertEquals;

/**
 * @author Tony Vaagenes
 */
public class PreGeneratedFileRegistryTestCase {

    private static final String BLOB = "Some blob";
    @Test
    public void importAndExport() {
        FileRegistry fileRegistry = new MockFileRegistry();
        fileRegistry.addFile("1234");
        fileRegistry.addBlob(ByteBuffer.wrap(BLOB.getBytes(StandardCharsets.UTF_8)));
        String serializedRegistry = PreGeneratedFileRegistry.exportRegistry(fileRegistry);

        PreGeneratedFileRegistry importedRegistry = PreGeneratedFileRegistry.importRegistry(new StringReader(serializedRegistry));

        assertEquals(Set.of("1234", "c5674b55c15c9c95.blob"), importedRegistry.getPaths());

        assertEquals(2, importedRegistry.getPaths().size());

        checkConsistentEntry(fileRegistry.export().get(0), importedRegistry);
        checkConsistentEntry(fileRegistry.export().get(1), importedRegistry);
        assertEquals(fileRegistry.fileSourceHost(), importedRegistry.fileSourceHost());
    }

    void checkConsistentEntry(FileRegistry.Entry entry, FileRegistry registry) {
        assertEquals(entry.reference, registry.addFile(entry.relativePath));
    }
}
