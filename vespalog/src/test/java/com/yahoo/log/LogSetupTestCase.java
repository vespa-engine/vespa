// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.log;

import com.yahoo.log.impl.LogUtils;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Make sure we can install the logging stuff properly.
 *
 * @author Bjorn Borud
 */
@SuppressWarnings({"deprecation", "removal"})
public class LogSetupTestCase {
    // For testing zookeeper log records
    protected static LogRecord zookeeperLogRecord;
    protected static LogRecord zookeeperLogRecordError;
    protected static LogRecord curatorLogRecord;
    protected static LogRecord curatorLogRecordError;
    protected static String zookeeperLogRecordString;
    protected static LogRecord notzookeeperLogRecord;

    protected static String hostname;
    protected static String pid;

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Before
    public void setUp() {
        System.setProperty("config.id", "my-test-config-id");
        System.setProperty("vespa.log.target", "file:test3");

        zookeeperLogRecord = new LogRecord(Level.WARNING, "zookeeper log record");
        zookeeperLogRecord.setLoggerName("org.apache.zookeeper.server.NIOServerCnxn");
        zookeeperLogRecord.setInstant(Instant.ofEpochMilli(1107011348029L));

        curatorLogRecord = new LogRecord(Level.WARNING, "curator log record");
        curatorLogRecord.setLoggerName("org.apache.curator.utils.DefaultTracerDriver");
        curatorLogRecord.setInstant(Instant.ofEpochMilli(1107011348029L));

        hostname = LogUtils.getHostName();
        pid = LogUtils.getPID();

        zookeeperLogRecordString = "1107011348.029000\t"
                + hostname
                + "\t"
                + pid
                + "/" + zookeeperLogRecord.getLongThreadID() + "\t-\t.org.apache.zookeeper.server.NIOServerCnxn"
                + "\twarning\tzookeeper log record";

        zookeeperLogRecordError = new LogRecord(Level.SEVERE, "zookeeper error");
        zookeeperLogRecordError.setLoggerName("org.apache.zookeeper.server.NIOServerCnxn");
        zookeeperLogRecordError.setInstant(Instant.ofEpochMilli(1107011348029L));

        curatorLogRecordError = new LogRecord(Level.SEVERE, "curator log record");
        curatorLogRecordError.setLoggerName("org.apache.curator.utils.DefaultTracerDriver");
        curatorLogRecordError.setInstant(Instant.ofEpochMilli(1107011348029L));

        notzookeeperLogRecord = new LogRecord(Level.WARNING, "not zookeeper log record");
        notzookeeperLogRecord.setLoggerName("org.apache.foo.Bar");
        notzookeeperLogRecord.setInstant(Instant.ofEpochMilli(1107011348029L));
    }

    @Test
    public void testSetup() throws IOException {
        try {
            final File zookeeperLogFile = folder.newFile("zookeeper.log");
            System.setProperty("zookeeper_log_file_prefix", zookeeperLogFile.getAbsolutePath());
            LogSetup.initVespaLogging("TST");
            Logger.getLogger("").log(VespaLogHandlerTestCase.record2);
            Logger.getLogger("").log(VespaLogHandlerTestCase.record1);
            Logger.getLogger("").log(VespaLogHandlerTestCase.record2);
            Logger.getLogger("").log(zookeeperLogRecord); // Should not be written to log, due to use of ZooKeeperFilter
            Logger.getLogger("").log(curatorLogRecord); // Should not be written to log, due to use of ZooKeeperFilter
            Logger.getLogger("").log(notzookeeperLogRecord);
            String[] lines = VespaLogHandlerTestCase.readFile("test3");
            assertEquals(2, lines.length);
            assertEquals(VespaLogHandlerTestCase.record1String, lines[0]);
        } finally {
            LogSetup.clearHandlers();
            LogSetup.getLogHandler().closeFileTarget();
            assertTrue(new File("test3").delete());
        }
    }

    // Note: This test will generate warnings about initVespaLogging being called twice, which can be ignored
    @Test
    public void testLogLevelSetting() {

        // levels are ordered like:
        // C++ has fatal, error, warning, config, info, event, debug, spam
        // Java has fatal, error, warning, event, config, info, debug, spam

        // logctl file values: fatal, error, warning, config, info, event, debug, spam

        System.setProperty("vespa.log.target", "fd:2");
        setupAndCheckLevels(null, "  ON  ON  ON  ON  ON  ON OFF OFF", LogLevel.INFO, LogLevel.DEBUG);
        setupAndCheckLevels("", "  ON  ON  ON  ON  ON  ON OFF OFF", LogLevel.INFO, LogLevel.DEBUG);
        setupAndCheckLevels("all", "  ON  ON  ON  ON  ON  ON  ON  ON", LogLevel.SPAM, null);
        setupAndCheckLevels("all -spam", "  ON  ON  ON  ON  ON  ON  ON OFF", LogLevel.DEBUG, LogLevel.SPAM);
        setupAndCheckLevels("all -debug", "  ON  ON  ON  ON  ON  ON OFF  ON", LogLevel.INFO, null);
        setupAndCheckLevels("all -debug -spam", "  ON  ON  ON  ON  ON  ON OFF OFF", LogLevel.INFO, LogLevel.DEBUG);
        // INFO is higher in level value than CONFIG, so one should rather use -info -config
        setupAndCheckLevels("all -debug -spam -info", "  ON  ON  ON  ON OFF  ON OFF OFF", LogLevel.WARNING, LogLevel.DEBUG);
        setupAndCheckLevels("all -debug -spam -info -config", "  ON  ON  ON OFF OFF  ON OFF OFF", LogLevel.WARNING, LogLevel.INFO);
        setupAndCheckLevels("all debug", "  ON  ON  ON  ON  ON  ON  ON  ON", LogLevel.SPAM, null);
        setupAndCheckLevels("debug", " OFF OFF OFF OFF OFF OFF  ON OFF", null, null);
        setupAndCheckLevels("debug error", " OFF  ON OFF OFF OFF OFF  ON OFF", LogLevel.ERROR, LogLevel.SPAM);
    }

    @Test
    public void testZooKeeperFilter() throws IOException {
        final File file = folder.newFile("zookeeper");
        LogSetup.ZooKeeperFilter filter = new LogSetup.ZooKeeperFilter(file.getAbsolutePath());
        assertFalse(filter.isLoggable(zookeeperLogRecord));
        //assertTrue(filter.isLoggable(zookeeperLogRecordError));
        assertTrue(filter.isLoggable(notzookeeperLogRecord));
        File actualLogFile = new File(file.getParent(), "zookeeper.0.log"); // Real file name will have .0.log appended
        String[] lines = VespaLogHandlerTestCase.readFile(actualLogFile.getAbsolutePath());
        assertEquals(1, lines.length);
        assertEquals(zookeeperLogRecordString, lines[0]);
    }

    private void setupAndCheckLevels(String levelString, String expectedOnOffString, Level shouldLog, Level shouldNotLog) {
        try {
            if (levelString != null) {
                System.setProperty("vespa.log.level", levelString);
            }
            LogSetup.initVespaLogging("TST");
            Logger.getLogger("TEST").log(LogLevel.DEBUG, "DEBUG");
            LevelController levelController = LogSetup.getLogHandler().getLevelControl("TST");
            assertNotNull(levelController);
            assertEquals(expectedOnOffString, levelController.getOnOffString());
            if (shouldLog != null) {
                assertTrue(levelController.shouldLog(shouldLog));
            }
            if (shouldNotLog != null) {
                assertFalse(levelController.shouldLog(shouldNotLog));
            }
        } finally {
            LogSetup.clearHandlers();
        }
    }
}
