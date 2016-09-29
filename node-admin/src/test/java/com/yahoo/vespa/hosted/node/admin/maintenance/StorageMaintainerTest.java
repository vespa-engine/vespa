package com.yahoo.vespa.hosted.node.admin.maintenance;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.FileOutputStream;
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
        writeFileWithBytes(writeSize);
        StorageMaintainer storageMaintainer = new StorageMaintainer();
        long usedBytes = storageMaintainer.getDiscUsedInBytes(folder.getRoot());
        if (usedBytes * 4 < writeSize ||
                usedBytes > writeSize * 4) fail("Used bytes is " + usedBytes + ", but wrote " + writeSize
                + " bytes, not even close.");
    }

    private void writeFileWithBytes(int writeSize) throws IOException {
        byte data[] = new byte[writeSize];
        FileOutputStream out = new FileOutputStream(folder.newFile());
        out.write(data);
        out.close();
    }
}