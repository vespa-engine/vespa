// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.application.provider;

import com.yahoo.config.application.api.FileRegistry;
import org.junit.Test;

import java.io.StringReader;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author Tony Vaagenes
 */
public class PreGeneratedFileRegistryTestCase {
    @Test
    public void importAndExport() {
        FileRegistry fileRegistry = new MockFileRegistry();
        String serializedRegistry = PreGeneratedFileRegistry.exportRegistry(fileRegistry);

        PreGeneratedFileRegistry importedRegistry =
                PreGeneratedFileRegistry.importRegistry(
                        new StringReader(serializedRegistry));

        assertTrue(importedRegistry.getPaths().containsAll(
                Arrays.asList(
                        MockFileRegistry.entry1.relativePath,
                        MockFileRegistry.entry2.relativePath)));

        assertEquals(2, importedRegistry.getPaths().size());

        checkConsistentEntry(MockFileRegistry.entry1, importedRegistry);
        checkConsistentEntry(MockFileRegistry.entry2, importedRegistry);

        assertEquals(fileRegistry.fileSourceHost(),
                importedRegistry.fileSourceHost());
    }

    void checkConsistentEntry(FileRegistry.Entry entry, FileRegistry registry) {
        assertEquals(entry.reference, registry.addFile(entry.relativePath));
    }
}
