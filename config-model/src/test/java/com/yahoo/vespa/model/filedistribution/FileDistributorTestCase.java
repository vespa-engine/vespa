// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.filedistribution;

import com.yahoo.config.FileReference;
import com.yahoo.config.model.api.FileDistribution;
import com.yahoo.config.model.application.provider.MockFileRegistry;
import com.yahoo.config.model.test.MockHosts;
import org.junit.Test;

import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * @author bratseth
 */
public class FileDistributorTestCase {

    @Test
    public void fileDistributor() {
        MockHosts hosts = new MockHosts();

        FileDistributor fileDistributor = new FileDistributor(new MockFileRegistry(), List.of(), false);

        String file1 = "component/path1";
        String file2 = "component/path2";
        FileReference ref1 = fileDistributor.sendFileToHost(file1, hosts.host1);
        fileDistributor.sendFileToHost(file1, hosts.host2); // same file reference as above
        FileReference ref2 = fileDistributor.sendFileToHost(file2, hosts.host3);

        assertEquals(new HashSet<>(Arrays.asList(hosts.host1, hosts.host2, hosts.host3)),
                fileDistributor.getTargetHosts());

        assertNotNull(ref1);
        assertNotNull(ref2);

        MockFileDistribution dbHandler = new MockFileDistribution();
        fileDistributor.sendDeployedFiles(dbHandler);
        assertEquals(3, dbHandler.filesToDownloadCalled); // One time for each host
    }

    private static class MockFileDistribution implements FileDistribution {
        int filesToDownloadCalled = 0;

        @Override
        public void startDownload(String hostName, int port, Set<FileReference> fileReferences) {
            filesToDownloadCalled++;
        }

        @Override
        public File getFileReferencesDir() {
            return null;
        }
    }

}
