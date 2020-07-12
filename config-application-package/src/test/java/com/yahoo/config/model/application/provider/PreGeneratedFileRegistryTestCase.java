// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.application.provider;

import com.yahoo.config.FileReference;
import com.yahoo.config.application.api.FileRegistry;
import org.junit.Test;

import java.io.StringReader;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author Tony Vaagenes
 */
public class PreGeneratedFileRegistryTestCase {

    @Test
    public void importAndExport() {
        FileRegistry fileRegistry = new MockFileRegistry();
        fileRegistry.addFile("1234");
        String serializedRegistry = PreGeneratedFileRegistry.exportRegistry(fileRegistry);

        PreGeneratedFileRegistry importedRegistry = PreGeneratedFileRegistry.importRegistry(new StringReader(serializedRegistry));

        FileReference fileReference = new FileReference("1234");
        assertTrue(importedRegistry.getPaths().containsAll(List.of("1234", fileReference)));

        assertEquals(1, importedRegistry.getPaths().size());

        checkConsistentEntry(fileRegistry.export().get(0), importedRegistry);
        assertEquals(fileRegistry.fileSourceHost(), importedRegistry.fileSourceHost());
    }

    void checkConsistentEntry(FileRegistry.Entry entry, FileRegistry registry) {
        assertEquals(entry.reference, registry.addFile(entry.relativePath));
    }
}
