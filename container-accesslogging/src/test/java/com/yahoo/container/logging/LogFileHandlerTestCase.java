// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.logging;

import com.yahoo.io.IOUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.SimpleFormatter;
import java.util.zip.GZIPInputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Bob Travis
 * @author bjorncs
 */
public class LogFileHandlerTestCase {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void testIt() throws IOException {
        File root = temporaryFolder.newFolder("logfilehandlertest");

        LogFileHandler h = new LogFileHandler();
        h.setFilePattern(root.getAbsolutePath() + "/logfilehandlertest.%Y%m%d%H%M%S");
        h.setFormatter(new Formatter() {
                public String format(LogRecord r) {
                    DateFormat df = new SimpleDateFormat("yyyy.MM.dd:HH:mm:ss.SSS");
                    String timeStamp = df.format(new Date(r.getMillis()));
                    return ("["+timeStamp+"]" + " " + formatMessage(r) + "\n");
                }
            } );
        long now = System.currentTimeMillis();
        long millisPerDay = 60*60*24*1000;
        long tomorrowDays = (now / millisPerDay) +1;
        long tomorrowMillis = tomorrowDays * millisPerDay;
        assertThat(tomorrowMillis).isEqualTo(h.getNextRotationTime(now));
        long[] rTimes = {1000, 2000, 10000};
        h.setRotationTimes(rTimes);
        assertThat(tomorrowMillis+1000).isEqualTo(h.getNextRotationTime(tomorrowMillis));
        assertThat(tomorrowMillis+10000).isEqualTo(h.getNextRotationTime(tomorrowMillis+3000));
        LogRecord lr = new LogRecord(Level.INFO, "test");
        h.publish(lr);
        h.publish(new LogRecord(Level.INFO, "another test"));
        h.rotateNow();
        h.publish(lr);
        h.flush();
        h.shutdown();
    }

    @Test
    public void testSimpleLogging() throws IOException {
        File logFile = temporaryFolder.newFile("testLogFileG1.txt");

      //create logfilehandler
      LogFileHandler h = new LogFileHandler();
      h.setFilePattern(logFile.getAbsolutePath());
      h.setFormatter(new SimpleFormatter());
      h.setRotationTimes("0 5 ...");

      //write log
      LogRecord lr = new LogRecord(Level.INFO, "testDeleteFileFirst1");
      h.publish(lr);
      h.flush();
      h.shutdown();
    }

    @Test
    public void testDeleteFileDuringLogging() throws IOException {
      File logFile = temporaryFolder.newFile("testLogFileG2.txt");

      //create logfilehandler
      LogFileHandler h = new LogFileHandler();
      h.setFilePattern(logFile.getAbsolutePath());
      h.setFormatter(new SimpleFormatter());
      h.setRotationTimes("0 5 ...");

      //write log
      LogRecord lr = new LogRecord(Level.INFO, "testDeleteFileDuringLogging1");
      h.publish(lr);
      h.flush();

      //delete log file
        logFile.delete();

      //write log again
      lr = new LogRecord(Level.INFO, "testDeleteFileDuringLogging2");
      h.publish(lr);
      h.flush();
      h.shutdown();
    }

    @Test(timeout = /*5 minutes*/300_000)
    public void testSymlink() throws IOException, InterruptedException {
        File root = temporaryFolder.newFolder("testlogforsymlinkchecking");
        LogFileHandler handler = new LogFileHandler();
        handler.setFilePattern(root.getAbsolutePath() + "/logfilehandlertest.%Y%m%d%H%M%S%s");
        handler.setFormatter(new Formatter() {
            public String format(LogRecord r) {
                DateFormat df = new SimpleDateFormat("yyyy.MM.dd:HH:mm:ss.SSS");
                String timeStamp = df.format(new Date(r.getMillis()));
                return ("["+timeStamp+"]" + " " + formatMessage(r) + "\n");
            }
        } );
        handler.setSymlinkName("symlink");

        handler.publish(new LogRecord(Level.INFO, "test"));
        String firstFile;
        do {
             Thread.sleep(1);
             firstFile = handler.getFileName();
        } while (firstFile == null);
        handler.rotateNow();
        String secondFileName;
        do {
            Thread.sleep(1);
            secondFileName = handler.getFileName();
        } while (firstFile.equals(secondFileName));

        handler.publish(new LogRecord(Level.INFO, "string which is way longer than the word test"));
        handler.waitDrained();
        assertThat(Files.size(Paths.get(firstFile))).isEqualTo(31);
        final long expectedSecondFileLength = 72;
        long secondFileLength;
        do {
            Thread.sleep(1);
            secondFileLength = Files.size(Paths.get(secondFileName));
        } while (secondFileLength != expectedSecondFileLength);

        long symlinkFileLength = Files.size(root.toPath().resolve("symlink"));
        assertThat(symlinkFileLength).isEqualTo(expectedSecondFileLength);
        handler.shutdown();
    }

    @Test
    public void testcompression() throws InterruptedException, IOException {
        File root = temporaryFolder.newFolder("testcompression");

        LogFileHandler h = new LogFileHandler(true);
        h.setFilePattern(root.getAbsolutePath() + "/logfilehandlertest.%Y%m%d%H%M%S%s");
        h.setFormatter(new Formatter() {
            public String format(LogRecord r) {
                DateFormat df = new SimpleDateFormat("yyyy.MM.dd:HH:mm:ss.SSS");
                String timeStamp = df.format(new Date(r.getMillis()));
                return ("["+timeStamp+"]" + " " + formatMessage(r) + "\n");
            }
        } );
        int logEntries = 10000;
        for (int i = 0; i < logEntries; i++) {
            LogRecord lr = new LogRecord(Level.INFO, "test");
            h.publish(lr);
        }
        h.waitDrained();
        String f1 = h.getFileName();
        assertThat(f1).startsWith(root.getAbsolutePath() + "/logfilehandlertest.");
        File uncompressed = new File(f1);
        File compressed = new File(f1 + ".gz");
        assertThat(uncompressed).exists();
        assertThat(compressed).doesNotExist();
        String content = IOUtils.readFile(uncompressed);
        assertThat(content).hasLineCount(logEntries);
        h.rotateNow();
        while (uncompressed.exists()) {
            Thread.sleep(1);
        }
        assertThat(compressed).exists();
        String unzipped = IOUtils.readAll(new InputStreamReader(new GZIPInputStream(new FileInputStream(compressed))));
        assertThat(content).isEqualTo(unzipped);
        h.shutdown();
    }

}
