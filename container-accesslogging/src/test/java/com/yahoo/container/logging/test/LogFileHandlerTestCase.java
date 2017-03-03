// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.logging.test;

import com.yahoo.container.logging.LogFileHandler;
import org.junit.Test;

import java.io.File;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.SimpleFormatter;

import static org.junit.Assert.assertEquals;

/**
 * @author <a href="mailto:travisb@yahoo-inc.com">Bob Travis</a>
 */
// TODO: Make these tests wait until the right things happen rather than waiting for a predetermined time
// These tests take too long, and are not cleaning up properly. See how this should be done in YApacheLogTestCase
public class LogFileHandlerTestCase {

    /**
     * The scenario
     */
    @Test
    public void testIt() {
        LogFileHandler h = new LogFileHandler();
        h.setFilePattern("./logfilehandlertest.%Y%m%d%H%M%S");
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
        assertEquals (tomorrowMillis, h.getNextRotationTime(now));
        long[] rTimes = {1000, 2000, 10000};
        h.setRotationTimes(rTimes);
        assertEquals (tomorrowMillis+1000, h.getNextRotationTime(tomorrowMillis));
        assertEquals (tomorrowMillis+10000, h.getNextRotationTime(tomorrowMillis+3000));
        boolean okToWrite = false; // don't want regular unit tests to create tiles....
        if (okToWrite) {
            LogRecord lr = new LogRecord(Level.INFO, "test");
            h.publish(lr);
            h.publish(new LogRecord(Level.INFO, "another test"));
            h.rotateNow();
            h.publish(lr);
            h.flush();
        }
    }

    private boolean delete(String fileOrDir) {
      File file = new File(fileOrDir);
      return file.delete();
    }

    // Feeble attempt to get rid of test dirs and files somewhat more reliably in these poorly written tests
    private void delete2(String fileOrDir) {
      File file = new File(fileOrDir);
      file.deleteOnExit();
    }

    @Test
    public void testDeleteFileFirst() {
      String logFilePattern = "./testLogFileG.txt";

      //delete log file
      delete(logFilePattern);

      //create logfilehandler
      LogFileHandler h = new LogFileHandler();
      h.setFilePattern(logFilePattern);
      h.setFormatter(new SimpleFormatter());
      h.setRotationTimes("0 5 ...");

      //write log
      LogRecord lr = new LogRecord(Level.INFO, "testDeleteFileFirst1");
      h.publish(lr);
      h.flush();

      //delete log file
      delete2(logFilePattern);
    }

    @Test
    public void testDeleteDirFirst() {
      //delete log file and dir
      delete("./testlogsG/delete/first/testlog");
      delete("./testlogsG/delete/first");
      delete("./testlogsG/delete");
      delete("./testlogsG");

      //create logfilehandler
      LogFileHandler h = new LogFileHandler();
      h.setFilePattern("./testlogsG/delete/first/testlog");
      h.setFormatter(new SimpleFormatter());
      h.setRotationTimes("0 5 ...");

      //write log
      LogRecord lr = new LogRecord(Level.INFO, "testDeleteDirFirst1");
      h.publish(lr);
      h.flush();

      //delete log file and dir
      delete2("./testlogsG/delete/first/testlog");
      delete2("./testlogsG/delete/first");
      delete2("./testlogsG/delete");
      delete2("./testlogsG");
    }

    @Test
    public void testDeleteFileDuringLogging() {
      String logFilePattern = "./testLogFileG.txt";

      //create logfilehandler
      LogFileHandler h = new LogFileHandler();
      h.setFilePattern(logFilePattern);
      h.setFormatter(new SimpleFormatter());
      h.setRotationTimes("0 5 ...");

      //write log
      LogRecord lr = new LogRecord(Level.INFO, "testDeleteFileDuringLogging1");
      h.publish(lr);
      h.flush();

      //delete log file
      delete(logFilePattern);

      //write log again
      lr = new LogRecord(Level.INFO, "testDeleteFileDuringLogging2");
      h.publish(lr);
      h.flush();

      //delete log file
      delete2(logFilePattern);
    }

    @Test
    public void testDeleteDirDuringLogging() {
      //create logfilehandler
      LogFileHandler h = new LogFileHandler();
      h.setFilePattern("./testlogsG/delete/during/testlog");
      h.setFormatter(new SimpleFormatter());
      h.setRotationTimes("0 5 ...");

      //write log
      LogRecord lr = new LogRecord(Level.INFO, "testDeleteDirDuringLogging1");
      h.publish(lr);
      h.flush();

      //delete log file and dir
      delete("./testlogsG/delete/during/testlog");
      delete("./testlogsG/delete/during");
      delete("./testlogsG/delete");
      delete("./testlogsG");

      //write log
      lr = new LogRecord(Level.INFO, "testDeleteDirDuringLogging2");
      h.publish(lr);
      h.flush();

      //delete log file and dir
      delete2("./testlogsG/delete/during/testlog");
      delete2("./testlogsG/delete/during");
      delete2("./testlogsG/delete");
      delete2("./testlogsG");
    }

    @Test
    public void testSymlink() {
        LogFileHandler h = new LogFileHandler();
        h.setFilePattern("./testlogforsymlinkchecking/logfilehandlertest.%Y%m%d%H%M%S%s");
        h.setFormatter(new Formatter() {
            public String format(LogRecord r) {
                DateFormat df = new SimpleDateFormat("yyyy.MM.dd:HH:mm:ss.SSS");
                String timeStamp = df.format(new Date(r.getMillis()));
                return ("["+timeStamp+"]" + " " + formatMessage(r) + "\n");
            }
        } );
        h.setSymlinkName("symlink");
        LogRecord lr = new LogRecord(Level.INFO, "test");
        h.publish(lr);
        String f1 = h.getFileName();
        try {
            while (f1 == null) {
                Thread.sleep(1);
                f1 = h.getFileName();
            }
            h.rotateNow();
            Thread.sleep(1);
            String f2 = h.getFileName();
            while (f1.equals(f2)) {
                Thread.sleep(1);
                f2 = h.getFileName();
            }
            lr = new LogRecord(Level.INFO, "string which is way longer than the word test");
            h.publish(lr);
            Thread.sleep(1000);
            File f = new File(f1);
            long first = f.length();
            f = new File(f2);
            long second = f.length();
            final long secondLength = 72;
            for (int n = 0; n < 20 && second != secondLength; ++n) {
                Thread.sleep(1000);
                second = f.length();
            }
            f = new File("./testlogforsymlinkchecking", "symlink");
            long link = f.length();
            assertEquals(secondLength, link);
            assertEquals(31, first);
            assertEquals(secondLength, second);
            delete2(f2);
        } catch (InterruptedException e) {
            // just let the test pass
        }
        delete2(f1);
        delete2("./testlogforsymlinkchecking/symlink");
        delete2("./testlogforsymlinkchecking");
    }

}
