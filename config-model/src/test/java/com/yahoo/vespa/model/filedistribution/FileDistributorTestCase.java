// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.filedistribution;

import com.yahoo.config.FileReference;
import com.yahoo.config.model.application.provider.MockFileRegistry;
import com.yahoo.config.model.test.MockHosts;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;

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

        FileReference ref1 = fileDistributor.sendFileToHosts("components/path1", Arrays.asList(hosts.host1, hosts.host2));
        FileReference ref2 = fileDistributor.sendFileToHosts("path2", Arrays.asList(hosts.host3));

        assertEquals(new HashSet<>(Arrays.asList(hosts.host1, hosts.host2, hosts.host3)),
                fileDistributor.getTargetHosts());

        assertTrue( ref1 != null );
        assertTrue( ref2 != null );
    }
}
