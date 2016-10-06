package com.yahoo.vespa.hosted.node.admin.maintenance;

import com.yahoo.vespa.hosted.node.maintenance.DeleteOldAppDataTest;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;

import static org.junit.Assert.*;

/**
 * @author dybis
 */
public class StorageMaintainerTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void testDiskUsed() throws IOException, InterruptedException {
        int writeSize = 10000;
        DeleteOldAppDataTest.writeNBytesToFile(folder.newFile(), writeSize);

        StorageMaintainer storageMaintainer = new StorageMaintainer();
        long usedBytes = storageMaintainer.getDiscUsedInBytes(folder.getRoot());
        if (usedBytes * 4 < writeSize || usedBytes > writeSize * 4)
            fail("Used bytes is " + usedBytes + ", but wrote " + writeSize + " bytes, not even close.");
    }
}