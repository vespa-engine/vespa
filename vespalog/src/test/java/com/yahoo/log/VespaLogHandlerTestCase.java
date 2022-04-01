// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.log;

import com.yahoo.log.impl.LogUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import static java.time.Instant.ofEpochSecond;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * @author  Bjorn Borud
 */
@SuppressWarnings("removal")
public class VespaLogHandlerTestCase {
    private final static String hostname;
    private final static String pid;

    static final LogRecord record1;
    static final String record1String;

    static final LogRecord record2;
    private static final String record2String;

    private static final LogRecord record3;
    private static final String record3String;

    private static final LogRecord record4;
    private static final String record4String;

    static {
        hostname = LogUtils.getHostName();
        pid = LogUtils.getPID();

        record1 = new LogRecord(Level.INFO, "This is a test");
        record1.setInstant(ofEpochSecond(1100011348L, 29_123_543));
        record1String = "1100011348.029123\t"
            + hostname
            + "\t"
	    + pid
            + "/"
            + record1.getLongThreadID()
            + "\tmy-test-config-id\tTST\tinfo\tThis is a test";

        record2 = new LogRecord(Level.FINE, "This is a test too");
        record2.setInstant(ofEpochSecond(1100021348L, 29_987_654));
        record2.setLoggerName("com.yahoo.log.test");
        record2String = "1100021348.029987\t"
            + hostname
            + "\t"
	    + pid
            + "/" + record2.getLongThreadID() + "\tmy-test-config-id\tTST.com.yahoo.log.test\tdebug\tThis is a test too";

        record3 = new LogRecord(Level.WARNING, "another test");
        record3.setLoggerName("com.yahoo.log.test");
        record3.setInstant(ofEpochSecond(1107011348L, 29_000_000L));
        record3String = "1107011348.029000\t"
            + hostname
            + "\t"
	    + pid
            + "/" + record3.getLongThreadID() + "\tmy-test-config-id\tTST.com.yahoo.log.test"
            + "\twarning\tanother test";

        record4 = new LogRecord(Level.WARNING, "unicode \u00E6\u00F8\u00E5 test \u7881 unicode");
        record4.setLoggerName("com.yahoo.log.test");
        record4.setInstant(ofEpochSecond(1107011348, 29_000_001L));
        record4String = "1107011348.029000\t"
            + hostname
            + "\t"
	    + pid
            + "/" + record4.getLongThreadID() + "\tmy-test-config-id\tTST.com.yahoo.log.test"
            + "\twarning\tunicode \u00E6\u00F8\u00E5 test \u7881 unicode";
    }

    @Before
    public void setUp() {
        // System.setProperty("vespa.log.level", "all");
        // System.setProperty("vespa.log.control.dir", ".");
        // System.setProperty("config.id", "my-test-config-id");
    }

    @After
    public void tearDown() {
        new File("./my-test-config-id.logcontrol").delete();
    }

    /**
     * Perform simple test
     */
    @Test
    public void testSimple () throws IOException {
        File logfile = new File("./test1");
        File ctlfile = new File("./my-test-config-id.logcontrol");
        try {
            logfile.delete();
            ctlfile.delete();
            VespaLogHandler h
                = new VespaLogHandler(new FileLogTarget(new File("test1")),
                    new VespaLevelControllerRepo(ctlfile.getName(), "all", "TST"),
                    "my-test-config-id", "TST");
            h.publish(record1);
            h.cleanup();
            h.close();

            File f = new File("test1");
            assertTrue(f.exists());

            String[] lines = readFile(f.getName());

            // make sure there is only one line in the file
            assertEquals(1, lines.length);

            // make sure that ine line matches what is expected
            assertEquals(record1String, lines[0]);
        }
        finally {
            logfile.delete();
            ctlfile.delete();
        }
    }

    @Test
    public void testFallback() {
        File file = new File("mydir2");
        file.delete();
        assertTrue(file.mkdir());
        try {
            new VespaLogHandler(new FileLogTarget(file), new VespaLevelControllerRepo("my-test-config-id.logcontrol", "all", "TST"), "my-test-config-id", "TST");
        } catch (Exception e) {
            fail("Should not throw exception: " + e.getMessage());
        }
    }

    @After
    public void cleanup() { new File("mydir2").delete(); }

    /**
     * Perform simple test
     */
    @Test
    public void testLogCtl () {
        MockLevelController ctl = new MockLevelController();
        MockLevelControllerRepo ctlRepo = new MockLevelControllerRepo(ctl);
        MockLogTarget target = new MockLogTarget();
        VespaLogHandler h = new VespaLogHandler(target,
                ctlRepo,
                "my-test-config-id", "TST");
        ctl.setShouldLog(Level.INFO);
        h.publish(record1);
        h.publish(record2);
        h.publish(record3);
        h.publish(record4);

        ctl.setShouldLog(Level.CONFIG);
        h.publish(record1);
        h.publish(record2);
        h.publish(record3);
        h.publish(record4);

        ctl.setShouldLog(Level.WARNING);
        h.publish(record1);
        h.publish(record2);
        h.publish(record3);
        h.publish(record4);

        ctl.setShouldLog(Level.FINE);
        h.publish(record1);
        h.publish(record2);
        h.publish(record3);
        h.publish(record4);

        h.close();
        String [] lines = target.getLines();
        assertEquals(4, lines.length);
        assertEquals(record1String, lines[0]);
        assertEquals(record3String, lines[1]);
        //assertEquals(record4String, lines[2]);
        assertEquals(record2String, lines[3]);
    }

    /**
     * Make sure rotation works
     */
    @Test
    public void testRotate () throws IOException {
        // Doesn't work in Windows. TODO: Fix the logging stuff
        if (System.getProperty("os.name").toLowerCase().contains("win"))
            return;
        try {
            VespaLogHandler h
                = new VespaLogHandler(new FileLogTarget(new File("test2")),
                    new VespaLevelControllerRepo("my-test-config-id.logcontrol", "all", "TST"), "my-test-config-id",
                                      "TST"
            );
            h.publish(record1);

            // before rename
            assertTrue(new File("test2").exists());
            assertFalse(new File("test2-rotated").exists());

            // rename file
            assertTrue("Could rename test2 to test2-rotated",
                       new File("test2").renameTo(new File("test2-rotated")));

            // log next entry
            h.publish(record2);
            h.cleanup();
            h.close();

            // now make sure both files exist
            assertTrue(new File("test2").exists());
            assertTrue(new File("test2-rotated").exists());

            // read both files
            String[] lines1 = readFile("test2");
            String[] lines2 = readFile("test2-rotated");

            // make sure they have the correct number of lines
            assertEquals(1, lines1.length);
            assertEquals(1, lines2.length);

            // make sure they match the correct strings
            assertEquals(record2String, lines1[0]);
            assertEquals(record1String, lines2[0]);
        }
        finally {
            new File("test2").delete();
            new File("test2-rotated").delete();
        }
    }


    /**
     * This test was made in order to look for a race condition
     * which occurs when file rotation in the VespaLogHandler
     * occurs concurrently (which it should never do).
     */
    @Test
    public void testRaceCondition () throws FileNotFoundException {
        int numThreads = 10;
        Thread[] t = new Thread[numThreads];
        final CyclicBarrier barrier = new CyclicBarrier(numThreads);
        final int numLogEntries = 100;

        try {
            final VespaLogHandler h =
                new VespaLogHandler(new FileLogTarget(new File("test4")),
                        new VespaLevelControllerRepo("my-test-config-id.logcontrol", "all", "TST"), "my-test-config-id",
                                    "TST"
                );

            class LogRacer implements Runnable {

                private LogRacer() {
                }

                public void run () {
                    try {
                        barrier.await();
                        logLikeCrazy();
                    }
                    catch (BrokenBarrierException | InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }

                void logLikeCrazy() {
                    for (int j = 0; j < numLogEntries; j++) {
                        try {
                            h.publish(record1);
                            h.publish(record2);
                        }
                        catch (Throwable t) {
                            fail(t.getMessage());
                        }
                    }
                }
            }

            for (int i = 0; i < numThreads; i++) {
                t[i] = new Thread(new LogRacer());
                t[i].start();
            }

            for (int i = 0; i < numThreads; i++) {
                t[i].join();
            }

            String[] lines = readFile("test4");
            assertTrue("num lines was " + lines.length
                       + " should be " +  (2 * numLogEntries * numThreads),
                       (lines.length == (2 * numLogEntries * numThreads)));

            h.cleanup();
            h.close();
        }
        catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        finally {
            new File("test4").delete();
        }

    }

    /**
     * Make sure unicode characters in log message works
     */
    @Test
    public void testUnicodeLog () throws IOException {
        try {
            VespaLogHandler h
                = new VespaLogHandler(new FileLogTarget(new File("test4")),
                    new VespaLevelControllerRepo("my-test-config-id.logcontrol", "all", "TST"), "my-test-config-id",
                                      "TST"
            );
            h.publish(record4);
            h.cleanup();
            h.close();

            // check that file exists
            assertTrue(new File("test4").exists());

            // read file
            String[] lines = readFile("test4");

            // make sure the file have the correct number of lines
            assertEquals(1, lines.length);

            // make sure the read lines match the correct string
            assertEquals(record4String, lines[0]);
        }
        finally {
            new File("test4").delete();
        }
    }

    /**
     * read a text file into a string array
     *
     */
    protected static String[] readFile (String fileName) {
        List<String> lines = new LinkedList<>();
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(new FileInputStream(new File(fileName)), StandardCharsets.UTF_8))) {
            for (String line = br.readLine();
                 line != null;
                 line = br.readLine()) {
                lines.add(line);
            }
            return lines.toArray(new String[0]);
        } catch (Throwable e) {
            return new String[0];
        }
    }

    private static class MockLevelControllerRepo implements LevelControllerRepo {
        private final LevelController levelController;
        MockLevelControllerRepo(LevelController controller) {
            this.levelController = controller;
        }

        @Override
        public LevelController getLevelController(String component) {
            return levelController;
        }

        @Override
        public void close() { }
    }

    private static class MockLevelController implements LevelController {

        private Level logLevel = Level.ALL;

        @Override
        public boolean shouldLog(Level level) {
            return (level.equals(logLevel));
        }

        void setShouldLog(Level level) {
            this.logLevel = level;
        }


        @Override
        public String getOnOffString() { return ""; }

        @Override
        public void checkBack() { }

        @Override
        public Level getLevelLimit() {
            return logLevel;
        }
    }

    private static class MockLogTarget implements LogTarget {
        private final ByteArrayOutputStream baos = new ByteArrayOutputStream();

        String[] getLines() {
            return baos.toString().split("\n");
        }
        @Override
        public OutputStream open() {
            return baos;
        }

        @Override
        public void close() {
            try {
                baos.close();
            } catch (IOException e) {
                fail("Test failed: " + e.getMessage());
            }
        }
    }
}
