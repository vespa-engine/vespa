// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/*
 * $Id$
 */
package com.yahoo.logserver.handlers.logmetrics;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.ArrayList;
import java.util.logging.Logger;
import java.util.logging.Level;

import com.yahoo.log.LogLevel;
import com.yahoo.log.event.Event;
import com.yahoo.logserver.filter.LevelFilter;
import com.yahoo.log.LogMessage;
import com.yahoo.logserver.handlers.AbstractLogHandler;

/**
 * The LogMetricsHandler stores a count of the number of log messages
 * per level per host and sends an event count for this five minutes.
 *
 * @author hmusum
 */
public class LogMetricsHandler extends AbstractLogHandler {
    private static final long EVENTINTERVAL = 5 * 60; // in seconds

    private static final Logger log = Logger.getLogger(LogMetricsHandler.class.getName());

    // A list of log metrics per host and per log level
    private final List<LevelCount> logMetrics = new ArrayList<LevelCount>();

    // The log levels that are handled by this plugin
    @SuppressWarnings("deprecation")
    private static final Level[] levels = {LogLevel.INFO,
            LogLevel.WARNING,
            LogLevel.SEVERE,
            LogLevel.ERROR,
            LogLevel.FATAL};


    /**
     * Constructor sets a default log filter ignoring the config,
     * debug and spam levels.
     */
    public LogMetricsHandler() {
        LevelFilter filter = new LevelFilter();

        for (Level level : Arrays.asList(levels)) {
            filter.addLevel(level);
        }

        setLogFilter(filter);

        // Start thread that sends events.
        EventGenerator eventThread = new EventGenerator();
        new Thread(eventThread).start();
    }

    public boolean doHandle(LogMessage message) {
        String host = message.getHost();
        Level logLevel = message.getLevel();

        boolean found = false;
        if (logMetrics.size() > 0) {
            LevelCount count;
            // Loop through the list logMetrics and check if there
            // exists an element with the same host and level.
            for (int i = 0; i < logMetrics.size(); i++) {
                count = logMetrics.get(i);
                if (count.getHost().equals(host) &&
                        count.getLevel().getName().equals(logLevel.getName())) {
                    count.addCount(1);
                    found = true;
                    break;
                }
            }
        }

        // There is no element in logMetrics with the same host and
        // level as in the message, so create a new object and add it
        // to the list.
        if (! found) {
            for (Level level : Arrays.asList(levels)) {
                LevelCount levelCount;
                if (level.getName().equals(logLevel.getName())) {
                    levelCount = new LevelCount(host,
                                                level,
                                                1);
                } else {
                    levelCount = new LevelCount(host,
                                                level,
                                                0);

                }
                logMetrics.add(levelCount);
            }
        }
        return true;
    }

    /**
     * Create event count for each log level and report it. For now we
     * add up the numbers for all host on each level and report that.
     */
    private void sendEvents() {
        Map<String, Long> levelCount = getMetricsPerLevel();
        for (Map.Entry<String, Long> entry : levelCount.entrySet()) {
            String key = entry.getKey();
            Long count = entry.getValue();
            Event.count("log_message." + key.toLowerCase(), count.longValue());
        }
    }

    public void flush() {}

    public void close() {}

    public String toString() {
        return LogMetricsHandler.class.getName();
    }

    /**
     * Returns the total number of log messages processed by this
     * plugin.
     *
     * @return A count of log messages
     */
    public long getMetricsCount() {
        long count = 0;
        for (LevelCount levelCount : logMetrics) {
            count = count + levelCount.getCount();
        }
        return count;
    }

    /**
     * Returns a Map of log level counts (level is key and count is
     * value).
     *
     * @return A Map of log level counts
     */
    public Map<String, Long> getMetricsPerLevel() {
        Map<String, Long> levelCounts = new TreeMap<String, Long>();
        // Loop through all levels summing the count for all hosts.
        for (Level level : Arrays.asList(levels)) {
            String levelName = level.getName();
            long count = 0;
            for (LevelCount levelCount : logMetrics) {
                if (levelName.equals(levelCount.getLevel().getName())) {
                    count += levelCount.getCount();
                }
            }
            levelCounts.put(levelName, count);
        }
        return levelCounts;
    }

    /**
     * The LevelCount class represents the number (count) of log
     * messages with the same log level for a host.
     */
    private class LevelCount {
        private final String host;
        private final Level level;
        private long count;

        LevelCount(String host, Level level, long count) {
            this.host = host;
            this.level = level;
            this.count = count;
        }

        LevelCount(String host, Level level) {
            this(host, level, 0);
        }

        Level getLevel() {
            return level;
        }

        String getHost() {
            return host;
        }

        long getCount() {
            return count;
        }

        void setCount(long count) {
            this.count = count;
        }

        void addCount(long add) {
            count += add;
        }

        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("Host=" + host + ", level = " + level.getName() +
                              ",count=" + count);
            return sb.toString();
        }
    }

    /**
     * Implements a thread that sends events every EVENTINTERVAL
     * seconds.
     */
    private class EventGenerator implements Runnable {
        public void run() {
            // Send events every EVENTINTERVAL seconds
            while (true) {
                try {
                    Thread.sleep(EVENTINTERVAL * 1000);
                } catch (InterruptedException e) {
                    log.log(Level.WARNING, e.getMessage());
                }
                sendEvents();
            }
        }
    }
}
