// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.filedistribution;

import com.yahoo.config.FileReference;
import com.yahoo.config.application.api.FileRegistry;
import com.yahoo.config.model.application.provider.MockFileRegistry;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;

/**
 * @author bratseth
 */
public class FileReferencesRepositoryTestCase {

    @Test
    public void fileDistributor() {
        FileRegistry fileRegistry = new MockFileRegistry();
        FileReferencesRepository fileReferencesRepository = new FileReferencesRepository();

        String file1 = "component/path1";
        String file2 = "component/path2";
        FileReference ref1 = fileRegistry.addFile(file1);
        FileReference ref2 = fileRegistry.addFile(file2);
        fileReferencesRepository.add(ref1);
        fileReferencesRepository.add(ref2);
        fileReferencesRepository.add(ref1); // same file reference as above
        fileReferencesRepository.add(ref2);

        assertNotNull(ref1);
        assertNotNull(ref2);
    }

}
