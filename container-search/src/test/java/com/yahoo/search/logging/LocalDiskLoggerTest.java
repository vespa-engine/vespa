// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.search.logging;

import com.yahoo.io.IOUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class LocalDiskLoggerTest {

    @TempDir
    Path tempDir;

    @Test
    public void testLocalDiskLogger() throws InterruptedException, IOException {
        File logFile = tempDir.resolve("localdisklogger.log").toFile();

        LocalDiskLoggerConfig.Builder builder = new LocalDiskLoggerConfig.Builder();
        builder.path(logFile.getAbsolutePath());
        LocalDiskLogger logger = new LocalDiskLogger(builder.build());

        logger.newEntry()
                .blob("Yo entry".getBytes())
                .send();
        waitForFile(logFile);

        String test = IOUtils.readAll(new FileReader(logFile));
        assertTrue(test.contains(Base64.getEncoder().encodeToString("Yo entry".getBytes())));
    }

    private void waitForFile(File file) throws InterruptedException {
        int waitFor = 10;
        while ( ! file.exists() && --waitFor > 0) {
            Thread.sleep(10);
        }
        if ( ! file.exists()) {
            fail("Local disk logger file was not created");
        }
    }

}
