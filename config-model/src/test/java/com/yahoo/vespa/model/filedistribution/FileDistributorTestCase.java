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
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author bratseth
 */
public class FileDistributorTestCase {
    @Test
    public void fileDistributor() {
        MockHosts hosts = new MockHosts();

        FileDistributor fileDistributor = new FileDistributor(new MockFileRegistry(), null);

        String file1 = "component/path1";
        String file2 = "component/path2";
        FileReference ref1 = fileDistributor.sendFileToHosts(file1, Arrays.asList(hosts.host1, hosts.host2));
        FileReference ref2 = fileDistributor.sendFileToHosts(file2, Arrays.asList(hosts.host3));

        assertEquals(new HashSet<>(Arrays.asList(hosts.host1, hosts.host2, hosts.host3)),
                fileDistributor.getTargetHosts());

        assertTrue( ref1 != null );
        assertTrue( ref2 != null );

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
