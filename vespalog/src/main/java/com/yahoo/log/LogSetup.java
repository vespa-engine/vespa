// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.log;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Enumeration;
import java.util.Timer;
import java.util.logging.FileHandler;
import java.util.logging.Filter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * Sets up Vespa logging. Call a setup method to set up this.
 *
 * @author  Bjorn Borud
 * @author arnej27959
 */
public class LogSetup {

    private static final Timer taskRunner = new Timer(true);

    static Timer getTaskRunner() { return taskRunner; }

    /** The log handler used by this */
    private static VespaLogHandler logHandler;

    private static ZooKeeperFilter zooKeeperFilter = null;

    /** Clear all handlers registered in java.util.logging framework */
    public static void clearHandlers () {
        Enumeration<String> names = LogManager.getLogManager().getLoggerNames();
        while (names.hasMoreElements()) {
            String name = names.nextElement();
            Logger logger = Logger.getLogger(name);

            Handler[] handlers = logger.getHandlers();
            for (Handler handler : handlers) {
                logger.removeHandler(handler);
            }
        }
    }

    private static boolean isInitialized = false;

    /**
     * Every Vespa application should call initVespaLogging exactly
     * one time.  This should be done from the main() method or from a
     * static initializer in the main class.  The library will pick up
     * the environment variables usually set by the Vespa
     * config-sentinel (VESPA_LOG_LEVEL, VESPA_LOG_TARGET,
     * VESPA_SERVICE_NAME, VESPA_LOG_CONTROL_DIR) but it's possible to
     * override these by setting system properties before calling
     * initVespaLogging.  This may be useful for unit testing etc:
     * <br>
     * System.setProperty("vespa.log.level", "all")
     * <br>
     * System.setProperty("vespa.log.target", "file:foo.log")
     * <br>
     * System.setProperty("vespa.service.name", "my.name")
     * <br>
     * System.setProperty("vespa.log.control.dir", ".")
     * <br>
     * System.setProperty("vespa.log.control.file", "my.logcontrol")
     * <br>
     * vespa.log.control.file is used if it's set, otherwise it's
     * vespa.log.control.dir + "/" + vespa.service.name + ".logcontrol"
     * if both of those variables are set, otherwise there will be no
     * runtime log control.
     *
     * @param programName the name of the program that is running;
     * this is added as a prefix to the logger name to form the
     * "component" part of the log message.  (Usually the logger name
     * is the name of the class that logs something, so the
     * programName should be kept short and simple.)
     **/
    public static void initVespaLogging(String programName) {
        if (isInitialized) {
            System.err.println("WARNING: initVespaLogging called twice");
        }
        isInitialized = true;

        // prefer Java system properties
        String logLevel   = System.getProperty("vespa.log.level");
        String logTarget  = System.getProperty("vespa.log.target");
        String logService = System.getProperty("vespa.service.name");
        String logControlDir  = System.getProperty("vespa.log.control.dir");
        String logControlFile = System.getProperty("vespa.log.control.file");
        if (programName == null || programName.equals("")) {
            throw new RuntimeException("invalid programName: " + programName);
        }

        // then try environment values
        if (logTarget == null)      logTarget = System.getenv("VESPA_LOG_TARGET");
        if (logService == null)     logService = System.getenv("VESPA_SERVICE_NAME");
        if (logControlDir == null)  logControlDir = System.getenv("VESPA_LOG_CONTROL_DIR");
        if (logControlFile == null) logControlFile = System.getenv("VESPA_LOG_CONTROL_FILE");
        if (logLevel == null)       logLevel = System.getenv("VESPA_LOG_LEVEL");

        // then hardcoded defaults
        if (logTarget == null) logTarget = "fd:2";
        if (logLevel == null) logLevel = "all -debug -spam";

        if (logControlFile == null &&
            logControlDir != null &&
            logService != null &&
            !logService.equals("") &&
            !logService.equals("-"))
        {
            logControlFile = logControlDir + "/" + logService + ".logcontrol";
        }

        // for backwards compatibility - XXX should be removed
        if (logService == null) logService = System.getProperty("config.id");
        if (logService == null) logService = "-";

        System.setProperty("vespa.service.name", logService);
        System.setProperty("vespa.program.name", programName);

        try {
            initInternal(logTarget, logService, logControlFile, programName, logLevel);
        } catch (FileNotFoundException e) {
            throw new RuntimeException("Unable to initialize logging", e);
        }
    }

    private static LogTarget getLogTargetFromString(String target) throws FileNotFoundException {
        if ("fd:2".equals(target)) {
            return new StderrLogTarget();
        } else if ("fd:1".equals(target)) {
            return new StdoutLogTarget();
        } else if (target.startsWith("file:")) {
            return new FileLogTarget(new File(target.substring(5)));
        }
        throw new IllegalArgumentException("Target '" + target + "' is not a valid target");
    }

    private static void initInternal(String target,
                                     String service,
                                     String logCtlFn,
                                     String app,
                                     String lev) throws FileNotFoundException {
        clearHandlers();

        if (app != null && app.length() > 64) app = app.substring(0, 63);

        if (logHandler != null) {
            logHandler.cleanup();
            Logger.getLogger("").removeHandler(logHandler);
        }
        Logger.getLogger("").setLevel(Level.ALL);
        logHandler = new VespaLogHandler(getLogTargetFromString(target), new VespaLevelControllerRepo(logCtlFn, lev, app), service, app);
        String zookeeperLogFile = System.getProperty("zookeeper_log_file_prefix");
        if (zookeeperLogFile != null) {
            zooKeeperFilter = new ZooKeeperFilter(zookeeperLogFile);
            logHandler.setFilter(zooKeeperFilter);
        }
        Logger.getLogger("").addHandler(logHandler);
    }

    static VespaLogHandler getLogHandler() {
        return logHandler;
    }

    /** perform cleanup */
    public static void cleanup() {
         if (zooKeeperFilter != null)
            zooKeeperFilter.close();
    }

    /**
     * Class that has an isLoggable methods that handles log records that
     * start with "org.apache.zookeeper." or "org.apache.curator"
     * (writing them to a log file with the prefix specified in the system property
     * zookeeper_log_file_prefix) and returning false.
     * For other log records, isLoggable returns true
     */
    static class ZooKeeperFilter implements Filter {

        private static final int FILE_SIZE = 10*1024*1024; // Max 10 Mb per log file
        private static final int maxFilesCount = 10; // Keep at most 10 log files

        private FileHandler fileHandler;

        ZooKeeperFilter(String logFilePrefix) {
            String logFilePattern = logFilePrefix + ".%g.log";
            try {
                fileHandler = new FileHandler(logFilePattern, FILE_SIZE, maxFilesCount, true);
                fileHandler.setFormatter(new VespaFormatter());
            } catch (IOException e) {
                System.out.println("Not able to create " + logFilePattern);
                fileHandler = null;
            }
        }

        /**
         * Return true if loggable (ordinary log record), returns false if this filter
         * logs the log record itself
         *
         * @param record a #{@link LogRecord}
         * @return true if loggable, false otherwise
         */
        @Override
        public boolean isLoggable(LogRecord record) {
            if (record.getLoggerName() == null) return true;
            if (!record.getLoggerName().startsWith("org.apache.zookeeper.") &&
                    !record.getLoggerName().startsWith("org.apache.curator")) {
                return true;
            }
            fileHandler.publish(record);
            return false;
        }

        public void close() {
            fileHandler.close();
        }
    }

}
