// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.filedistribution;

import com.yahoo.config.FileReference;
import com.yahoo.config.model.application.provider.MockFileRegistry;
import com.yahoo.config.model.test.MockHosts;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * @author bratseth
 */
public class FileDistributorTestCase {

    @Test
    public void fileDistributor() {
        MockHosts hosts = new MockHosts();
        FileDistributor fileDistributor = new FileDistributor(new MockFileRegistry());

        String file1 = "component/path1";
        String file2 = "component/path2";
        FileReference ref1 = fileDistributor.sendFileToHost(file1, hosts.host1);
        fileDistributor.sendFileToHost(file1, hosts.host2); // same file reference as above
        FileReference ref2 = fileDistributor.sendFileToHost(file2, hosts.host3);

        assertEquals(new HashSet<>(Arrays.asList(hosts.host1, hosts.host2, hosts.host3)),
                fileDistributor.getTargetHosts());

        assertNotNull(ref1);
        assertNotNull(ref2);
    }

}
