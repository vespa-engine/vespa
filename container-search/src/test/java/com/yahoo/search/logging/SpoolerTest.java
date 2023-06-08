// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.logging;

import com.yahoo.test.ManualClock;
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

    private static final ManualClock clock = new ManualClock();

    @TempDir
    Path tempDir;

    @Test
    public void testSpoolingLogger() throws IOException {
        Path spoolDir = tempDir.resolve("spool");

        Spooler spooler = createSpooler(spoolDir, 1);

        TestLogger logger = new TestLogger(spooler);
        assertTrue(sendEntry(logger, "Yo entry"));
        assertTrue(sendEntry(logger, "Yo entry 2"));

        Path readyPath = spooler.readyPath();
        Path readyFile1 = readyPath.resolve(spooler.fileNameBase.get() + "-0");
        waitUntilFileExists(readyFile1);
        Path readyFile2 = readyPath.resolve(spooler.fileNameBase.get() + "-1");
        waitUntilFileExists(readyFile2);

        // Check content after being moved to ready path
        assertContent(readyFile1, "Yo entry");
        assertContent(readyFile2, "Yo entry 2");

        // Process files (read, transport files)
        logger.manualRun();
        assertEquals(2, logger.entriesSent());

        // No files in processing or ready, 2 files in successes
        assertProcessedFiles(spooler, 0);
        assertReadyFiles(spooler, 0);
        assertSuccessFiles(spooler, 2);
        assertFailureFiles(spooler, 0);

        assertTrue(spooler.failures().isEmpty(), spooler.failures().toString());
    }

    @Test
    public void testSpoolingLoggerCleanup() throws IOException {
        Path spoolDir = tempDir.resolve("spool");

        Spooler spooler = createSpooler(spoolDir, 1, false, 5);

        TestLogger logger = new TestLogger(spooler);
        assertTrue(sendEntry(logger, "Yo entry"));

        Path readyPath = spooler.readyPath();
        Path readyFile1 = readyPath.resolve(spooler.fileNameBase.get() + "-0");
        waitUntilFileExists(readyFile1);

        // Check content after being moved to ready path
        assertContent(readyFile1, "Yo entry");

        // Process files (read, transport files)
        logger.manualRun();
        assertEquals(1, logger.entriesSent());

        // No files in processing or ready or successes
        assertProcessedFiles(spooler, 0);
        assertReadyFiles(spooler, 0);
        assertSuccessFiles(spooler, 0);
        assertFailureFiles(spooler, 0);

        assertTrue(spooler.failures().isEmpty(), spooler.failures().toString());
    }

    @Test
    public void testSpoolingManyEntriesPerFile() throws IOException {
        Path spoolDir = tempDir.resolve("spool");

        Spooler spooler = createSpooler(spoolDir, 2);

        TestLogger logger = new TestLogger(spooler);
        assertTrue(sendEntry(logger, "Yo entry"));
        assertTrue(sendEntry(logger, "Yo entry 2"));

        Path readyPath = spooler.readyPath();
        Path readyFile1 = readyPath.resolve(spooler.fileNameBase.get() + "-0");
        waitUntilFileExists(readyFile1);

        // Check content after being moved to ready path
        assertContent(readyFile1, "Yo entry");

        // Process files (read, transport files)
        logger.manualRun();
        assertEquals(2, logger.entriesSent());

        // No files in processing or ready, 1 file in successes
        assertProcessedFiles(spooler, 0);
        assertReadyFiles(spooler, 0);
        assertSuccessFiles(spooler, 1);
        assertFailureFiles(spooler, 0);

        // Write 1 entry and advance time, so that file will be processed even if
        // maxEntriesPerFile is 2 and there is only 1 entry in file
        assertTrue(sendEntry(logger, "Yo entry 3"));
        clock.advance(Duration.ofMinutes(1));
        logger.manualRun();
        assertEquals(3, logger.entriesSent());
        assertSuccessFiles(spooler, 2);
    }

    private void assertProcessedFiles(Spooler spooler, int expected) throws IOException {
        assertEquals(expected, spooler.listFilesInPath(spooler.processingPath()).size());
    }

    private void assertReadyFiles(Spooler spooler, int expected) throws IOException {
        assertEquals(expected, spooler.listFilesInPath(spooler.readyPath()).size());
    }

    private void assertSuccessFiles(Spooler spooler, int expected) throws IOException {
        assertEquals(expected, spooler.listFilesInPath(spooler.successesPath()).size());
    }

    private void assertFailureFiles(Spooler spooler, int expected) throws IOException {
        assertEquals(expected, spooler.listFilesInPath(spooler.failuresPath()).size());
    }

    @Test
    public void failingToTransportIsRetried() throws IOException {
        Path spoolDir = tempDir.resolve("spool");
        Spooler spooler = createSpooler(spoolDir, 1, true, 2);
        FailingToTransportNthEntryLogger logger = new FailingToTransportNthEntryLogger(spooler, 2);

        assertTrue(sendEntry(logger, "Yo entry"));
        logger.manualRun(); // Success for first message
        assertEquals(1, spooler.listFilesInPath(spooler.successesPath()).size());

        assertTrue(sendEntry(logger, "Yo entry 2"));
        logger.manualRun(); // Failure for second message, so still just 1 file in successes path
        assertEquals(1, spooler.listFilesInPath(spooler.successesPath()).size());
        assertEquals(0, spooler.listFilesInPath(spooler.failuresPath()).size());

        logger.manualRun(); // Success when retrying second message, so 2 files in successes path
        assertEquals(2, spooler.listFilesInPath(spooler.successesPath()).size());
    }

    @Test
    public void failingToTransportGivesUpAfterNTries() throws IOException {
        Path spoolDir = tempDir.resolve("spool");
        Spooler spooler = createSpooler(spoolDir, 1, true, 2);
        FailingToTransportAfterNEntriesLogger logger = new FailingToTransportAfterNEntriesLogger(spooler, 2);

        assertTrue(sendEntry(logger, "Yo entry"));
        assertEquals(1, spooler.listFilesInPath(spooler.readyPath()).size());
        logger.manualRun(); // Success for first message
        assertEquals(1, spooler.listFilesInPath(spooler.successesPath()).size());
        assertEquals(0, spooler.listFilesInPath(spooler.failuresPath()).size());

        assertTrue(sendEntry(logger, "Yo entry 2"));
        assertEquals(1, spooler.listFilesInPath(spooler.readyPath()).size());
        logger.manualRun(); // Failure for second message, so still just 1 file in successes path
        assertEquals(1, spooler.listFilesInPath(spooler.successesPath()).size());
        assertEquals(0, spooler.listFilesInPath(spooler.failuresPath()).size());

        logger.manualRun(); // Fails again, but should be retried
        assertEquals(1, spooler.listFilesInPath(spooler.readyPath()).size());
        assertEquals(1, spooler.listFilesInPath(spooler.successesPath()).size());
        assertEquals(0, spooler.listFilesInPath(spooler.failuresPath()).size());

        logger.manualRun(); // Fails again, should be moved to failures path
        assertEquals(0, spooler.listFilesInPath(spooler.readyPath()).size());
        assertEquals(1, spooler.listFilesInPath(spooler.successesPath()).size());
        assertEquals(1, spooler.listFilesInPath(spooler.failuresPath()).size());
    }

    @Test
    public void noSuccessFiles() throws IOException {
        Path spoolDir = tempDir.resolve("spool");
        boolean keepSuccessFiles = false;
        Spooler spooler = createSpooler(spoolDir, 1, keepSuccessFiles, 2);
        FailingToTransportNthEntryLogger logger = new FailingToTransportNthEntryLogger(spooler, 2);

        assertTrue(sendEntry(logger, "Yo entry"));
        logger.manualRun(); // Success for first message
        assertEquals(0, spooler.listFilesInPath(spooler.successesPath()).size());
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

        assertTrue(path.toFile().exists(), path.toFile() + " does not exits");
    }

    private void assertContent(Path file, String expectedContent) throws IOException {
        String content = Files.readString(file);
        assertTrue(content.contains(Base64.getEncoder().encodeToString(expectedContent.getBytes())));
    }

    private static Spooler createSpooler(Path spoolDir, int maxEntriesPerFile) {
        return new Spooler(spoolDir, maxEntriesPerFile, clock, true, 1000);
    }

    private static Spooler createSpooler(Path spoolDir, int maxEntriesPerFile, boolean keepSuccessFiles, int maxFailures) {
        return new Spooler(spoolDir, maxEntriesPerFile, clock, keepSuccessFiles, maxFailures);
    }

    private static class TestLogger extends AbstractSpoolingLogger {

        private final List<LoggerEntry> entriesSent = new ArrayList<>();

        public TestLogger(Spooler spooler) {
            super(spooler);
        }

        @Override
        public boolean transport(LoggerEntry entry) {
            entriesSent.add(entry);
            return true;
        }

        @Override
        public void run() {} // do nothing, call manualRun() to do something

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

    private static class FailingToTransportNthEntryLogger extends AbstractSpoolingLogger {

        private int transportCount = 0;
        private final int entriesToFail;

        public FailingToTransportNthEntryLogger(Spooler spooler, int entriesToFail) {
            super(spooler);
            this.entriesToFail = entriesToFail;
        }

        @Override
        public boolean send(LoggerEntry entry) {
            spooler.write(entry);
            return true;
        }

        @Override
        public boolean transport(LoggerEntry entry) {
            transportCount++;
            return transportCount != entriesToFail;
        }

        @Override
        public void run() {} // do nothing, call manualRun() to do something

        public void manualRun() {
            super.run();
        }

    }

    private static class FailingToTransportAfterNEntriesLogger extends AbstractSpoolingLogger {

        private int transportCount = 0;
        private final int entriesToFailAfter;

        public FailingToTransportAfterNEntriesLogger(Spooler spooler, int entriesToFailAfter) {
            super(spooler);
            this.entriesToFailAfter = entriesToFailAfter;
        }

        @Override
        public boolean send(LoggerEntry entry) {
            spooler.write(entry);
            return true;
        }

        @Override
        public boolean transport(LoggerEntry entry) {
            transportCount++;
            return transportCount < entriesToFailAfter;
        }

        @Override
        public void run() {} // do nothing, call manualRun() to do something

        public void manualRun() {
            super.run();
        }

    }

}
