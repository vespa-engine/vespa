package com.yahoo.vespa.hosted.node.admin.maintenance;

import com.yahoo.metrics.simple.MetricReceiver;
import com.yahoo.vespa.hosted.dockerapi.metrics.MetricReceiverWrapper;
import com.yahoo.vespa.hosted.node.admin.util.Environment;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

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
        writeNBytesToFile(folder.newFile(), writeSize);

        Environment environment = new Environment.Builder().build();
        StorageMaintainer storageMaintainer = new StorageMaintainer(null,
                new MetricReceiverWrapper(MetricReceiver.nullImplementation), environment);
        long usedBytes = storageMaintainer.getDiscUsedInBytes(folder.getRoot());
        if (usedBytes * 4 < writeSize || usedBytes > writeSize * 4)
            fail("Used bytes is " + usedBytes + ", but wrote " + writeSize + " bytes, not even close.");
    }

    private static void writeNBytesToFile(File file, int nBytes) throws IOException {
        Files.write(file.toPath(), new byte[nBytes]);
    }
}