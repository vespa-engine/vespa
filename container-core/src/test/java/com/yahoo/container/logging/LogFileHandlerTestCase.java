// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.logging;

import com.yahoo.compress.ZstdCompressor;
import com.yahoo.container.logging.LogFileHandler.Compression;
import com.yahoo.io.IOUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.function.BiFunction;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;

import static com.yahoo.yolean.Exceptions.uncheck;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Bob Travis
 * @author bjorncs
 */
public class LogFileHandlerTestCase {
    private static final int BUFFER_SIZE = 0x10000;

    @TempDir
    public File temporaryFolder;

    @Test
    void testIt() throws IOException {
        File root = newFolder(temporaryFolder, "logfilehandlertest");

        String pattern = root.getAbsolutePath() + "/logfilehandlertest.%Y%m%d%H%M%S";
        long[] rTimes = {1000, 2000, 10000};
        LogFileHandler<String> h = new LogFileHandler<>(Compression.NONE, BUFFER_SIZE, pattern, rTimes, null, 2048, "thread-name", new StringLogWriter());
        long now = System.currentTimeMillis();
        long millisPerDay = 60 * 60 * 24 * 1000;
        long tomorrowDays = (now / millisPerDay) + 1;
        long tomorrowMillis = tomorrowDays * millisPerDay;

        assertEquals(tomorrowMillis + 1000, h.logThread.getNextRotationTime(tomorrowMillis));
        assertEquals(tomorrowMillis + 10000, h.logThread.getNextRotationTime(tomorrowMillis + 3000));
        String message = "test";
        h.publish(message);
        h.publish("another test");
        h.rotateNow();
        h.publish(message);
        h.flush();
        h.shutdown();
    }

    @Test
    void testSimpleLogging() throws IOException {
        File logFile = File.createTempFile("testLogFileG1.txt", null, temporaryFolder);

        //create logfilehandler
        LogFileHandler<String> h = new LogFileHandler<>(Compression.NONE, BUFFER_SIZE, logFile.getAbsolutePath(), "0 5 ...", null, 2048, "thread-name", new StringLogWriter());

        //write log
        h.publish("testDeleteFileFirst1");
        h.flush();
        h.shutdown();
    }

    @Test
    void testDeleteFileDuringLogging() throws IOException {
        File logFile = File.createTempFile("testLogFileG2.txt", null, temporaryFolder);

        //create logfilehandler
        LogFileHandler<String> h = new LogFileHandler<>(Compression.NONE, BUFFER_SIZE, logFile.getAbsolutePath(), "0 5 ...", null, 2048, "thread-name", new StringLogWriter());

        //write log
        h.publish("testDeleteFileDuringLogging1");
        h.flush();

        //delete log file
        logFile.delete();

        //write log again
        h.publish("testDeleteFileDuringLogging2");
        h.flush();
        h.shutdown();
    }

    @Test
    @Timeout(300_000)
    void testSymlink() throws IOException, InterruptedException {
        File root = newFolder(temporaryFolder, "testlogforsymlinkchecking");
        Formatter formatter = new Formatter() {
            public String format(LogRecord r) {
                DateFormat df = new SimpleDateFormat("yyyy.MM.dd:HH:mm:ss.SSS");
                String timeStamp = df.format(new Date(r.getMillis()));
                return ("[" + timeStamp + "]" + " " + formatMessage(r));
            }
        };
        LogFileHandler<String> handler = new LogFileHandler<>(
                Compression.NONE, BUFFER_SIZE, root.getAbsolutePath() + "/logfilehandlertest.%Y%m%d%H%M%S%s", new long[]{0}, "symlink", 2048, "thread-name", new StringLogWriter());

        String message = formatter.format(new LogRecord(Level.INFO, "test"));
        handler.publishAndWait(message);
        String firstFile = handler.getFileName();
        handler.rotateNow();
        String secondFileName = handler.getFileName();
        assertNotEquals(firstFile, secondFileName);

        String longMessage = formatter.format(new LogRecord(Level.INFO, "string which is way longer than the word test"));
        handler.publish(longMessage);
        handler.flush();
        assertEquals(31, Files.size(Paths.get(firstFile)));
        final long expectedSecondFileLength = 72;

        long symlinkFileLength = Files.size(root.toPath().resolve("symlink"));
        assertEquals(expectedSecondFileLength, symlinkFileLength);
        handler.shutdown();
    }

    @Test
    @Timeout(300_000)
    void compresses_previous_log_file() throws InterruptedException, IOException {
        File root = newFolder(temporaryFolder, "compressespreviouslogfile");
        LogFileHandler<String> firstHandler = new LogFileHandler<>(
                Compression.ZSTD, BUFFER_SIZE, root.getAbsolutePath() + "/compressespreviouslogfile.%Y%m%d%H%M%S%s", new long[]{0}, "symlink", 2048, "thread-name", new StringLogWriter());
        firstHandler.publishAndWait("test");
        firstHandler.shutdown();

        assertEquals(5, Files.size(Paths.get(firstHandler.getFileName())));
        assertEquals(root.toPath().resolve("symlink").toRealPath().toString(),
                Paths.get(firstHandler.getFileName()).toRealPath().toString());

        LogFileHandler<String> secondHandler = new LogFileHandler<>(
                Compression.ZSTD, BUFFER_SIZE, root.getAbsolutePath() + "/compressespreviouslogfile.%Y%m%d%H%M%S%s", new long[]{0}, "symlink", 2048, "thread-name", new StringLogWriter());
        secondHandler.publishAndWait("test");
        secondHandler.rotateNow();

        assertEquals(root.toPath().resolve("symlink").toRealPath().toString(),
                Paths.get(secondHandler.getFileName()).toRealPath().toString());
        while (Files.exists(root.toPath().resolve(firstHandler.getFileName()))) Thread.sleep(1);

        assertTrue(Files.exists(Paths.get(firstHandler.getFileName() + ".zst")));
        secondHandler.shutdown();
    }

    @Test
    @Timeout(300_000)
    void testcompression_gzip() throws InterruptedException, IOException {
        testcompression(
                Compression.GZIP, "gz",
                (compressedFile, __) -> uncheck(() -> new String(new GZIPInputStream(Files.newInputStream(compressedFile)).readAllBytes())));
    }

    @Test
    @Timeout(300_000)
    void testcompression_zstd() throws InterruptedException, IOException {
        testcompression(
                Compression.ZSTD, "zst",
                (compressedFile, uncompressedSize) -> uncheck(() -> {
                    ZstdCompressor zstdCompressor = new ZstdCompressor();
                    byte[] uncompressedBytes = new byte[uncompressedSize];
                    byte[] compressedBytes = Files.readAllBytes(compressedFile);
                    zstdCompressor.decompress(compressedBytes, 0, compressedBytes.length, uncompressedBytes, 0, uncompressedBytes.length);
                    return new String(uncompressedBytes);
                }));
    }

    private void testcompression(Compression compression,
                                 String fileExtension,
                                 BiFunction<Path, Integer, String> decompressor) throws IOException, InterruptedException {
        File root = newFolder(temporaryFolder, "testcompression" + compression.name());

        LogFileHandler<String> h = new LogFileHandler<>(
                compression, BUFFER_SIZE, root.getAbsolutePath() + "/logfilehandlertest.%Y%m%d%H%M%S%s", new long[]{0}, null, 2048, "thread-name", new StringLogWriter());
        int logEntries = 10000;
        for (int i = 0; i < logEntries; i++) {
            h.publish("test");
        }
        h.flush();
        String f1 = h.getFileName();
        assertTrue(f1.startsWith(root.getAbsolutePath() + "/logfilehandlertest."));
        File uncompressed = new File(f1);
        File compressed = new File(f1 + "." + fileExtension);
        assertTrue(uncompressed.exists());
        assertFalse(compressed.exists());
        String content = IOUtils.readFile(uncompressed);
        assertEquals(logEntries, content.lines().count());
        h.rotateNow();
        while (uncompressed.exists()) {
            Thread.sleep(1);
        }
        assertTrue(compressed.exists());
        String uncompressedContent = decompressor.apply(compressed.toPath(), content.getBytes().length);
        assertEquals(uncompressedContent, content);
        h.shutdown();
    }

    @Test
    void testSizeBasedRotation() throws IOException, InterruptedException {
        File root = newFolder(temporaryFolder, "testsizerotation");
        String pattern = root.getAbsolutePath() + "/logfilehandlertest.%Y%m%d%H%M%S%s";
        
        // Set rotation size to 1KB and check interval to 100ms for faster testing
        long rotationSize = 1024; // 1KB
        long sizeCheckInterval = 100; // Check every 100ms for testing
        
        LogFileHandler<String> handler = new LogFileHandler<>(
                Compression.NONE, BUFFER_SIZE, pattern, new long[]{0}, null, 2048, 
                "thread-name", new StringLogWriter(), rotationSize, sizeCheckInterval);
        
        // Write initial message to trigger file creation
        handler.publish("initial");
        handler.flush();
        Thread.sleep(100);
        
        String firstFile = handler.getFileName();
        assertNotNull(firstFile, "File should be created after first write");
        
        // Write data that exceeds rotation size
        String largeMessage = "x".repeat(100); // 100 bytes per message
        for (int i = 0; i < 15; i++) { // 1500 bytes total, exceeds 1024
            handler.publish(largeMessage);
        }
        handler.flush();
        
        // Wait for size check to occur
        Thread.sleep(200);
        
        // Publish one more message to trigger the check during publish
        handler.publish("trigger");
        handler.flush();
        
        // Verify rotation occurred
        String currentFile = handler.getFileName();
        assertNotEquals(firstFile, currentFile, "File should have rotated due to size");
        
        // Verify first file exists and is not empty
        assertTrue(Files.exists(Paths.get(firstFile)));
        assertTrue(Files.size(Paths.get(firstFile)) > 0);
        
        handler.shutdown();
    }
    
    @Test
    void testSizeRotationDisabled() throws IOException, InterruptedException {
        File root = newFolder(temporaryFolder, "testsizerotationdisabled");
        String pattern = root.getAbsolutePath() + "/logfilehandlertest.%Y%m%d%H%M%S%s";
        
        // Rotation size = 0 means disabled
        LogFileHandler<String> handler = new LogFileHandler<>(
                Compression.NONE, BUFFER_SIZE, pattern, new long[]{0}, null, 2048, 
                "thread-name", new StringLogWriter(), 0, 1000);
        
        // Write initial message to trigger file creation
        handler.publish("initial");
        handler.flush();
        Thread.sleep(100);
        
        String firstFile = handler.getFileName();
        assertNotNull(firstFile, "File should be created after first write");
        
        // Write lots of data
        String largeMessage = "x".repeat(1000);
        for (int i = 0; i < 100; i++) { // 100KB total
            handler.publish(largeMessage);
        }
        handler.flush();
        Thread.sleep(100);
        
        // Verify no rotation occurred
        assertEquals(firstFile, handler.getFileName(), "File should not rotate when size rotation is disabled");
        
        handler.shutdown();
    }
    
    @Test
    void testSizeCheckInterval() throws IOException, InterruptedException {
        File root = newFolder(temporaryFolder, "testsizecheckinterval");
        String pattern = root.getAbsolutePath() + "/logfilehandlertest.%Y%m%d%H%M%S%s";
        
        // Set rotation size to 1KB but with a long check interval
        long rotationSize = 1024; // 1KB
        long sizeCheckInterval = 5000; // Check every 5 seconds
        
        LogFileHandler<String> handler = new LogFileHandler<>(
                Compression.NONE, BUFFER_SIZE, pattern, new long[]{0}, null, 2048, 
                "thread-name", new StringLogWriter(), rotationSize, sizeCheckInterval);
        
        // Write initial message to trigger file creation
        handler.publish("initial");
        handler.flush();
        Thread.sleep(100);
        
        String firstFile = handler.getFileName();
        assertNotNull(firstFile, "File should be created after first write");
        
        // Write data that exceeds rotation size
        String largeMessage = "x".repeat(100);
        for (int i = 0; i < 15; i++) { // 1500 bytes total
            handler.publish(largeMessage);
        }
        handler.flush();
        
        // Wait shorter than check interval
        Thread.sleep(1000);
        handler.publish("test");
        handler.flush();
        
        // Should NOT have rotated yet (within check interval)
        assertEquals(firstFile, handler.getFileName(), "File should not rotate before check interval");
        
        // Wait for check interval to pass
        Thread.sleep(4500);
        handler.publish("trigger check");
        handler.flush();
        
        // Now it should have rotated
        assertNotEquals(firstFile, handler.getFileName(), "File should rotate after check interval");
        
        handler.shutdown();
    }
    
    @Test
    void testSizeAndTimeRotation() throws IOException, InterruptedException {
        File root = newFolder(temporaryFolder, "testsizeandtimerotation");
        String pattern = root.getAbsolutePath() + "/logfilehandlertest.%Y%m%d%H%M%S%s";
        
        // Configure both size and time rotation
        long rotationSize = 2048; // 2KB
        long sizeCheckInterval = 100;
        // Use rotation times that won't trigger during test
        long[] rotationTimes = {System.currentTimeMillis() + 3600000}; // 1 hour from now
        
        LogFileHandler<String> handler = new LogFileHandler<>(
                Compression.NONE, BUFFER_SIZE, pattern, rotationTimes, null, 2048, 
                "thread-name", new StringLogWriter(), rotationSize, sizeCheckInterval);
        
        // Write initial message to trigger file creation
        handler.publish("initial");
        handler.flush();
        Thread.sleep(100);
        
        String firstFile = handler.getFileName();
        assertNotNull(firstFile, "File should be created after first write");
        
        // Write less than rotation size
        String message = "x".repeat(100);
        for (int i = 0; i < 10; i++) { // 1000 bytes, less than 2KB
            handler.publish(message);
        }
        handler.flush();
        Thread.sleep(200);
        
        // Should not have rotated (neither size nor time triggered)
        assertEquals(firstFile, handler.getFileName());
        
        // Now exceed size limit
        for (int i = 0; i < 15; i++) { // Additional 1500 bytes, total 2500 bytes > 2KB
            handler.publish(message);
        }
        handler.flush();
        
        // Wait for size check to occur
        Thread.sleep(200);
        
        // Publish another message to trigger the check during publish
        handler.publish("trigger");
        handler.flush();
        
        // Wait a bit for rotation to complete
        Thread.sleep(200);
        
        // Should have rotated due to size
        String secondFile = handler.getFileName();
        assertNotEquals(firstFile, secondFile, "File should rotate due to size limit");
        
        handler.shutdown();
    }
    
    @Test
    void testSizeRotationWithCompression() throws IOException, InterruptedException {
        File root = newFolder(temporaryFolder, "testsizerotationcompression");
        String pattern = root.getAbsolutePath() + "/logfilehandlertest.%Y%m%d%H%M%S%s";
        
        LogFileHandler<String> handler = new LogFileHandler<>(
                Compression.ZSTD, BUFFER_SIZE, pattern, new long[]{0}, null, 2048, 
                "thread-name", new StringLogWriter(), 1024, 100);
        
        // Write initial message to trigger file creation
        handler.publish("initial");
        handler.flush();
        Thread.sleep(100);
        
        String firstFile = handler.getFileName();
        assertNotNull(firstFile, "File should be created after first write");
        
        // Write data exceeding rotation size
        String message = "x".repeat(100);
        for (int i = 0; i < 20; i++) { // 2000 bytes
            handler.publish(message);
        }
        handler.flush();
        
        // Wait for size check to occur
        Thread.sleep(200);
        
        // Publish another message to trigger the check during publish
        handler.publish("trigger");
        handler.flush();
        Thread.sleep(100);
        
        // Get the current file after rotation
        String secondFile = handler.getFileName();
        assertNotEquals(firstFile, secondFile, "File should have rotated due to size");
        
        // Force another rotation to trigger compression of the previous file
        handler.rotateNow();
        
        // Wait for compression to complete
        int maxWaitTime = 5000;
        int waited = 0;
        while (Files.exists(Paths.get(firstFile)) && waited < maxWaitTime) {
            Thread.sleep(100);
            waited += 100;
        }
        
        // Verify first file was compressed
        assertFalse(Files.exists(Paths.get(firstFile)), "Original file should be deleted after compression");
        assertTrue(Files.exists(Paths.get(firstFile + ".zst")), "Compressed file should exist");
        
        handler.shutdown();
    }
    
    @Test
    void testMultipleSizeRotations() throws IOException, InterruptedException {
        File root = newFolder(temporaryFolder, "testmultiplesizerotations");
        String pattern = root.getAbsolutePath() + "/logfilehandlertest.%Y%m%d%H%M%S%s";
        
        LogFileHandler<String> handler = new LogFileHandler<>(
                Compression.NONE, BUFFER_SIZE, pattern, new long[]{0}, null, 2048, 
                "thread-name", new StringLogWriter(), 512, 50); // Small size, fast checks
        
        // Track all created files
        String message = "x".repeat(50);
        
        // Create multiple rotations
        for (int rotation = 0; rotation < 3; rotation++) {
            for (int i = 0; i < 12; i++) { // 600 bytes per rotation
                handler.publish(message);
            }
            handler.flush();
            Thread.sleep(100); // Wait for size check
            handler.publish("trigger" + rotation);
        }
        
        handler.shutdown();
        
        // Count log files created
        List<Path> logFiles = Files.list(root.toPath())
                .filter(p -> p.toString().contains("logfilehandlertest"))
                .collect(Collectors.toList());
        
        assertTrue(logFiles.size() >= 3, "Should have created at least 3 log files due to size rotation");
    }
    
    @Test
    void testBackwardCompatibility() throws IOException {
        // Test that old constructor still works (without size parameters)
        File root = newFolder(temporaryFolder, "testbackwardcompat");
        String pattern = root.getAbsolutePath() + "/logfilehandlertest.%Y%m%d%H%M%S";
        
        // Use old constructor
        LogFileHandler<String> handler = new LogFileHandler<>(
                Compression.NONE, BUFFER_SIZE, pattern, "0 5 ...", null, 2048, 
                "thread-name", new StringLogWriter());
        
        // Should work normally without size-based rotation
        handler.publish("test backward compatibility");
        handler.flush();
        
        assertTrue(Files.exists(Paths.get(handler.getFileName())));
        
        handler.shutdown();
    }

    static class StringLogWriter implements LogWriter<String> {

        @Override
        public void write(String record, OutputStream outputStream) throws IOException {
            outputStream.write(record.getBytes(StandardCharsets.UTF_8));
        }

    }

    private static File newFolder(File root, String... subDirs) throws IOException {
        String subFolder = String.join("/", subDirs);
        File result = new File(root, subFolder);
        if (!result.mkdirs()) {
            throw new IOException("Couldn't create folders " + root);
        }
        return result;
    }
}
