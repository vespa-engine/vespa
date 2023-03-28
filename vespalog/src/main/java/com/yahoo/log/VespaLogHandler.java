// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.log;

import java.io.UnsupportedEncodingException;
import java.util.Map;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.StreamHandler;

import static java.util.Map.entry;
import static java.util.logging.Level.FINE;
import static java.util.logging.Level.INFO;

/**
 * @author Bjorn Borud
 * @author arnej27959
 */
@SuppressWarnings("deprecation")
class VespaLogHandler extends StreamHandler {

    // Reduce log level for some loggers
    private static final Function<Level, Level> INFO_TO_FINE = level -> level == INFO ? FINE : level;
    private static final Map<String, Function<Level, Level>> loggersWithAlteredLogLevel = Map.ofEntries(
            entry("com.yahoo.vespa.spifly.repackaged.spifly.BaseActivator", INFO_TO_FINE),
            entry("org.eclipse.jetty.server.Server", INFO_TO_FINE),
            entry("org.eclipse.jetty.server.handler.ContextHandler", INFO_TO_FINE),
            entry("org.eclipse.jetty.server.AbstractConnector", INFO_TO_FINE),
            entry("org.eclipse.jetty.util.HostPort", __ -> FINE)
    );

    private final LogTarget logTarget;
    private final String serviceName;
    private final String appPrefix;
    private final LevelControllerRepo repo;
    private final RejectFilter logRejectFilter;

    /**
     * Construct handler which logs to specified logTarget.  The logTarget
     * may be of the following formats:
     *
     * <DL>
     *  <DT> <code>fd:&lt;number&gt;</code>
     *  <DD> Log to specified file descriptor number.  Only "fd:2"
     *       is supported.
     *
     *  <DT> <code>file:&lt;filename&gt;</code>
     *  <DD> Log to specified file in append mode
     * </DL>
     */
    VespaLogHandler(LogTarget logTarget,
                    LevelControllerRepo levelControllerRepo, String serviceName, String applicationPrefix) {
        this.logTarget = logTarget;
        this.serviceName = serviceName;
        this.appPrefix = applicationPrefix;
        this.repo = levelControllerRepo;
        this.logRejectFilter = RejectFilter.createDefaultRejectFilter();
        initialize();
    }

    /**
     * Publish a log record into the Vespa log target.
     */
    @Override
    public synchronized void publish(LogRecord record) {
        String loggerName = record.getLoggerName();
        Level level = possiblyReduceLogLevel(loggerName, record.getLevel());

        LevelController ctrl = getLevelControl(loggerName);
        if (!ctrl.shouldLog(level)) {
            return;
        }

        if (logRejectFilter.shouldReject(record.getMessage())) {
            return;
        }

        try {
            // provokes rotation of target
            setOutputStream(logTarget.open());
        } catch (RuntimeException e) {
            LogRecord r = new LogRecord(Level.SEVERE, "Unable to open file target");
            r.setThrown(e);
            emergencyLog(r);
            setOutputStream(System.err);
        }
        super.publish(record);
        flush();
        closeFileTarget();
    }

    private static Level possiblyReduceLogLevel(String loggerName, Level level) {
        if (loggerName == null) return level;

        var levelMapper = loggersWithAlteredLogLevel.get(loggerName);
        return levelMapper == null ? level : levelMapper.apply(level);
    }

    LevelController getLevelControl(String component) {
        return repo.getLevelController(component);
    }

    /**
     * Initialize the handler.  The main invariant is that
     * outputStream is always set to something valid when this method
     * returns.
     */
    private void initialize () {
        try {
            setFormatter(new VespaFormatter(serviceName, appPrefix));
            setLevel(LogLevel.ALL);
            setEncoding("UTF-8");
            // System.err.println("initialize vespa logging, default level: "+defaultLogLevel);
            setOutputStream(logTarget.open());
        }
        catch (UnsupportedEncodingException uee) {
            LogRecord r = new LogRecord(Level.SEVERE, "Unable to set log encoding to UTF-8");
            r.setThrown(uee);
            emergencyLog(r);
        }
        catch (RuntimeException e) {
            LogRecord r = new LogRecord(Level.SEVERE, "Unable to open file target");
            r.setThrown(e);
            emergencyLog(r);
            setOutputStream(System.err);
        }
    }


    /** Closes the target log file, if there is one */
    synchronized void closeFileTarget() {
        try {
            logTarget.close();
        }
        catch (RuntimeException e) {
            LogRecord r = new LogRecord(Level.WARNING, "Unable to close log");
            r.setThrown(e);
            emergencyLog(r);
        }
    }

    /**
     * If the logging system experiences problems we can't be expected
     * to log it through normal channels, so we have an emergency log
     * method which just uses STDERR for formatting the log messages.
     * (Which might be right, and might be wrong).
     *
     * @param record The log record to be logged
     */
    private void emergencyLog(LogRecord record) {
        record.setLoggerName(VespaLogHandler.class.getName());
        System.err.println(getFormatter().format(record));
    }

    public void cleanup() {
        repo.close();
    }
}
