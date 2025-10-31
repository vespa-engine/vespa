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
import java.util.function.BiFunction;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.zip.GZIPInputStream;

import static com.yahoo.yolean.Exceptions.uncheck;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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
