// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.filedistribution;

import com.yahoo.config.FileReference;
import com.yahoo.config.application.api.FileRegistry;
import com.yahoo.config.model.application.provider.MockFileRegistry;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;

/**
 * @author bratseth
 */
public class FileDistributorTestCase {

    @Test
    public void fileDistributor() {
        FileRegistry fileRegistry = new MockFileRegistry();
        FileDistributor fileDistributor = new FileDistributor();

        String file1 = "component/path1";
        String file2 = "component/path2";
        FileReference ref1 = fileRegistry.addFile(file1);
        FileReference ref2 = fileRegistry.addFile(file2);
        fileDistributor.sendFileReference(ref1);
        fileDistributor.sendFileReference(ref2);
        fileDistributor.sendFileReference(ref1); // same file reference as above
        fileDistributor.sendFileReference(ref2);

        assertNotNull(ref1);
        assertNotNull(ref2);
    }

}
