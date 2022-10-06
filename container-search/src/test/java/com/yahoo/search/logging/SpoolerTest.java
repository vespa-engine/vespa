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
        assertTrue(sendEntry(logger, "Yo entry"));
        assertTrue(sendEntry(logger, "Yo entry 2"));

        Path readyPath = spooler.readyPath();
        Path readyFile1 = readyPath.resolve("1");
        waitUntilFileExists(readyFile1);
        Path readyFile2 = readyPath.resolve("2");
        waitUntilFileExists(readyFile2);

        // Check content after being moved to ready path
        assertContent(readyFile1, "Yo entry");
        assertContent(readyFile2, "Yo entry 2");

        // Process files (read, transport files)
        logger.manualRun();
        assertEquals(2, logger.entriesSent());

        // No files in processing or ready, 2 files in successes
        assertEquals(0, spooler.listFilesInPath(spooler.processingPath()).size());
        assertEquals(0, spooler.listFilesInPath(readyPath).size());
        assertEquals(2, spooler.listFilesInPath(spooler.successesPath()).size());
        assertEquals(0, spooler.listFilesInPath(spooler.failuresPath()).size());
    }

    @Test
    public void failingToTransportIsRetried() throws IOException {
        Path spoolDir = tempDir.resolve("spool");
        Spooler spooler = new Spooler(spoolDir);
        FailingToTransportSecondEntryLogger logger = new FailingToTransportSecondEntryLogger(spooler);

        assertTrue(sendEntry(logger, "Yo entry"));
        logger.manualRun(); // Success for first message
        assertEquals(1, spooler.listFilesInPath(spooler.successesPath()).size());

        assertTrue(sendEntry(logger, "Yo entry 2"));
        logger.manualRun(); // Failure for second message, so still just 1 file in successes path
        assertEquals(1, spooler.listFilesInPath(spooler.successesPath()).size());

        logger.manualRun(); // Success when retrying second message, so 2 files in successes path
        assertEquals(2, spooler.listFilesInPath(spooler.successesPath()).size());
    }

    private boolean sendEntry(Logger logger, String x) {
        return logger.newEntry()
                     .blob(x.getBytes())
                     .send();
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

    private void assertContent(Path file, String expectedContent) throws IOException {
        String content = Files.readString(file);
        assertTrue(content.contains(Base64.getEncoder().encodeToString(expectedContent.getBytes())));
    }

    private static class TestLogger extends AbstractSpoolingLogger {

        private final List<LoggerEntry> entriesSent = new ArrayList<>();

        public TestLogger(Spooler spooler) {
            super(spooler);
        }

        @Override
        boolean transport(LoggerEntry entry) {
            entriesSent.add(entry);
            return true;
        }

        @Override
        public void run() {
            // Do nothing, use manualRun
        }

        @Override
        public boolean send(LoggerEntry entry) {
            spooler.write(entry);
            return true;
        }

        public void manualRun() {
            super.run();
        }

        int entriesSent() {
            return entriesSent.size();
        }

    }

    private static class FailingToTransportSecondEntryLogger extends AbstractSpoolingLogger {

        private int transportCount = 0;

        public FailingToTransportSecondEntryLogger(Spooler spooler) {
            super(spooler);
        }

        @Override
        public boolean send(LoggerEntry entry) {
            spooler.write(entry);
            return true;
        }

        @Override
        boolean transport(LoggerEntry entry) {
            transportCount++;
            return transportCount != 2;
        }

        @Override
        public void run() {
            // do nothing
        }

        public void manualRun() {
            super.run();
        }

    }

}
