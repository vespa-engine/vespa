// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.logging;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SpoolerTest {

    @TempDir
    Path tempDir;

    @Test
    public void testSpoolingLogger() throws IOException {
        Path spoolDir = tempDir.resolve("spool");

        Spooler spooler = new Spooler(spoolDir);

        TestLogger logger = new TestLogger(spooler);
        assertTrue(logger.newEntry()
                         .blob("Yo entry".getBytes())
                         .send());
        assertTrue(logger.newEntry()
                         .blob("Yo entry 2".getBytes())
                         .send());

        Path readyPath = spooler.readyPath();
        Path readyFile1 = readyPath.resolve("1");
        waitUntilFileExists(readyFile1);
        Path readyFile2 = readyPath.resolve("2");
        waitUntilFileExists(readyFile2);

        // Check content after being moved to ready path
        String content = Files.readString(readyFile1);
        assertTrue(content.contains(Base64.getEncoder().encodeToString("Yo entry".getBytes())));
        assertTrue(Files.readString(readyFile2).contains(Base64.getEncoder().encodeToString("Yo entry 2".getBytes())));

        // Process files (read, transport files)
        logger.manualRun();
        assertEquals(2, logger.entriesSent());

        // No files in processing or ready, 2 files in successes
        assertEquals(0, spooler.listFilesInPath(spooler.processingPath()).size());
        assertEquals(0, spooler.listFilesInPath(readyPath).size());
        assertEquals(2, spooler.listFilesInPath(spooler.successesPath()).size());
        assertEquals(0, spooler.listFilesInPath(spooler.failuresPath()).size());
    }

    private void waitUntilFileExists(Path path) {
        Instant end = Instant.now().plus(Duration.ofSeconds(1));
        while (!path.toFile().exists() && Instant.now().isBefore(end)) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        assertTrue(path.toFile().exists());
    }


    private static class TestLogger extends AbstractSpoolingLogger {

        private final List<LoggerEntry> entriesSent = new ArrayList<>();

        public TestLogger(Spooler spooler) {
            super(spooler);
        }

        @Override
        void transport(LoggerEntry entry) {
            System.out.println("Called transport()");
            entriesSent.add(entry);
        }

        @Override
        public void run() {
            // Do nothing, use manualRun
        }

        @Override
        public boolean send(LoggerEntry entry) {
            return spooler.write(entry);
        }

        public void manualRun() {
            super.run();
        }

        int entriesSent() {
            return entriesSent.size();
        }

    }

}
