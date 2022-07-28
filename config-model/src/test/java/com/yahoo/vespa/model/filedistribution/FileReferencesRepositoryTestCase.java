// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.filedistribution;

import com.yahoo.config.FileReference;
import com.yahoo.config.application.api.FileRegistry;
import com.yahoo.config.model.application.provider.MockFileRegistry;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * @author bratseth
 */
public class FileReferencesRepositoryTestCase {

    @Test
    void fileDistributor() {
        FileRegistry fileRegistry = new MockFileRegistry();
        FileReferencesRepository fileReferencesRepository = new FileReferencesRepository(fileRegistry);

        String file1 = "component/path1";
        String file2 = "component/path2";
        FileReference ref1 = fileRegistry.addFile(file1);
        FileReference ref2 = fileRegistry.addFile(file2);

        assertEquals(Set.of(ref1, ref2), fileReferencesRepository.allFileReferences());
        assertNotNull(ref1);
        assertNotNull(ref2);
    }

}
