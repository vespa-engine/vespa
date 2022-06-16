// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.logserver.handlers.archive;

import com.yahoo.log.LogMessage;
import com.yahoo.logserver.handlers.AbstractLogHandler;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Iterator;
import java.util.TimeZone;
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * This class implements a log handler which archives the incoming
 * messages based on their timestamp.  The goal of this archiver
 * is to make it easy to locate messages in a time interval, while
 * ensuring that no log file exceeds the maximum allowed size.
 * <p>
 * This class is not thread safe.
 * </p>
 *
 * @author Bjorn Borud
 */
public class ArchiverHandler extends AbstractLogHandler {
    private static final Logger log = Logger.getLogger(ArchiverHandler.class.getName());

    /**
     * File instance representing root directory for logging
     */
    private File root;

    /**
     * Root directory for logging
     */
    private String absoluteRootDir;

    /**
     * Max number of log files open at any given time
     */
    private static final int maxFilesOpen = 100;

    /**
     * The maximum number of bytes we allow a file to grow to
     * before we rotate it
     */
    private int maxFileSize;

    /**
     * Calendar instance for operating on Date objects
     */
    private final Calendar calendar;

    /**
     * DateFormat instance for building filenames
     */
    private final SimpleDateFormat dateformat;

    /**
     * This is an LRU cache for LogWriter objects.  Remember that
     * we have one LogWriter for each time slot
     */
    private final LogWriterLRUCache logWriterLRUCache;

    private FilesArchived filesArchived;

    /**
     * Creates an ArchiverHandler
     */
    private ArchiverHandler() {
        calendar = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
        dateformat = new SimpleDateFormat("yyyy/MM/dd/HH");
        dateformat.setTimeZone(TimeZone.getTimeZone("UTC"));

        setLogFilter(null);

        // set up LRU for files
        logWriterLRUCache = new LogWriterLRUCache(maxFilesOpen, (float) 0.75);
    }

    /**
     * Creates an ArchiverHandler which puts files under
     * the given root directory.
     */
    public ArchiverHandler(String rootDir, int maxFileSize, String zip) {
        this();
        setRootDir(rootDir, zip);
        this.maxFileSize = maxFileSize;
    }


    /**
     * Return the appropriate LogWriter given a log message.
     */
    private synchronized LogWriter getLogWriter(LogMessage m) throws IOException {
        Integer slot = dateHash(m.getTimestamp().toEpochMilli());
        LogWriter logWriter = logWriterLRUCache.get(slot);
        if (logWriter != null) {
            return logWriter;
        }

        // invariant: LogWriter we sought was not in the cache
        logWriter = new LogWriter(getPrefix(m), maxFileSize, filesArchived);
        logWriterLRUCache.put(slot, logWriter);

        return logWriter;
    }

    /**
     * This method is just a fancy way of generating a stripped
     * down number representing year, month, day and hour in order
     * to partition logging in time.
     * <p>
     * This method is not thread-safe.
     */
    public int dateHash(long time) {
        calendar.setTimeInMillis(time);
        int year = calendar.get(Calendar.YEAR);
        int month = calendar.get(Calendar.MONTH) + 1;
        int day = calendar.get(Calendar.DAY_OF_MONTH);
        int hour = calendar.get(Calendar.HOUR_OF_DAY);

        return year * 1000000
                + month * 10000
                + day * 100
                + hour;
    }

    /**
     * Generate prefix for log filenames based on log message.
     * <p>
     * <EM>This message is <code>public</code> only because we need
     * access to it in unit tests.  For all practical purposes this
     * method does not exist to you, the application programmer, OK? :-)</EM>
     * <p>
     * XXX optimize!
     */
    public String getPrefix(LogMessage msg) {
        calendar.setTimeInMillis(msg.getTimestamp().toEpochMilli());
        StringBuilder result = new StringBuilder(absoluteRootDir.length()
                                                       + 1 // slash
                                                       + 4 // year
                                                       + 1 // slash
                                                       + 2 // month
                                                       + 1 // slash
                                                       + 2 // day
                                                       + 1 // slash
                                                       + 2 // hour
        );
        result.append(absoluteRootDir).append("/")
              .append(dateformat.format(calendar.getTime()));
        return result.toString();
    }

    public boolean doHandle(LogMessage msg) {
        try {
            LogWriter logWriter = getLogWriter(msg);
            logWriter.write(msg.toString());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return true;
    }

    public synchronized void flush() {
        for (LogWriter l : logWriterLRUCache.values()) {
            try {
                l.flush();
            } catch (IOException e) {
                log.log(Level.WARNING, "Flushing failed", e);
            }
        }
    }

    public synchronized void close() {
        Iterator<LogWriter> it = logWriterLRUCache.values().iterator();
        while (it.hasNext()) {
            LogWriter l = it.next();
            try {
                l.close();
            } catch (IOException e) {
                log.log(Level.WARNING, "Closing failed", e);
            }
            it.remove();
        }
    }

    private void setRootDir(String rootDir, String zip) {
        // roundabout way of setting things, but this way we can
        // get around Java's ineptitude for file handling (relative paths in File are broken)
        absoluteRootDir = new File(rootDir).getAbsolutePath();
        root = new File(absoluteRootDir);

        // ensure that root dir exists
        if (root.isDirectory()) {
            log.log(Level.FINE, () -> "Using " + absoluteRootDir + " as root");
        } else {
            if (! root.mkdirs()) {
                log.log(Level.SEVERE, "Unable to create directory " + absoluteRootDir);
            } else {
                log.log(Level.FINE, () -> "Created root at " + absoluteRootDir);
            }
        }
        filesArchived = new FilesArchived(root, zip);
    }

    public String toString() {
        return ArchiverHandler.class.getName() + ": root=" + absoluteRootDir;
    }
}
