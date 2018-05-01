// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.logging;

import com.yahoo.container.core.AccessLogConfig;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.StreamHandler;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * <p>Implements log file naming/rotating logic for container logs.</p>
 *
 * <p>Overridden methods: publish</p>
 *
 * <p>Added methods: setFilePattern, setRotationTimes, rotateNow (+ few others)</p>
 *
 * @author Bob Travis
 */
public class LogFileHandler extends StreamHandler {

    /** True to use the sequence file name scheme, false (default) to use the date scheme */
    private final boolean useSequenceNameScheme;
    private final boolean compressOnRotation;
    private long[] rotationTimes = {0}; //default to one log per day, at midnight
    private String filePattern = "./log.%T";  // default to current directory, ms time stamp
    private long lastRotationTime = -1; // absolute time (millis since epoch) of current file start
    private int numberOfRecords = -1;
    private long nextRotationTime = 0;
    private OutputStream currentOutputStream = null;
    private String fileName;
    private String symlinkName = null;
    private ArrayBlockingQueue<LogRecord> logQueue = new ArrayBlockingQueue<>(100000);
    LogRecord rotateCmd = new LogRecord(Level.SEVERE, "rotateNow");

    static private class LogThread extends Thread {
        LogFileHandler logFileHandler;
        long lastFlush = 0;
        public LogThread(LogFileHandler logFile) {
            super("Logger");
            setDaemon(true);
            logFileHandler = logFile;
        }
        @Override
        public void run() {
            try {
                storeLogRecords();
            } catch (InterruptedException e) {
            } catch (Exception e) {
                com.yahoo.protect.Process.logAndDie("Failed storing log records", e);
            }

            logFileHandler.flush();
        }

        private void storeLogRecords() throws InterruptedException {
            while (!isInterrupted()) {
                LogRecord r = logFileHandler.logQueue.poll(100, TimeUnit.MILLISECONDS);
                if (r != null) {
                    if (r == logFileHandler.rotateCmd) {
                        logFileHandler.internalRotateNow();
                        lastFlush = System.nanoTime();
                    } else {
                        logFileHandler.internalPublish(r);
                    }
                    flushIfOld(3, TimeUnit.SECONDS);
                } else {
                    flushIfOld(100, TimeUnit.MILLISECONDS);
                }
            }
        }

        private void flushIfOld(long age, TimeUnit unit) {
            long now = System.nanoTime();
            if (TimeUnit.NANOSECONDS.toMillis(now - lastFlush) > unit.toMillis(age)) {
                logFileHandler.flush();
                lastFlush = now;
            }
        }
    }
    LogThread logThread = null;

    public LogFileHandler() {
        this(AccessLogConfig.FileHandler.RotateScheme.Enum.DATE, false);
    }

    public LogFileHandler(boolean compressOnRotation) {
        this(AccessLogConfig.FileHandler.RotateScheme.Enum.DATE, compressOnRotation);
    }

    public LogFileHandler(AccessLogConfig.FileHandler.RotateScheme.Enum rotateScheme) {
        this(rotateScheme, false);
    }

    public LogFileHandler(AccessLogConfig.FileHandler.RotateScheme.Enum rotateScheme,
                          boolean compressOnRotation)
    {
        super();
        this.useSequenceNameScheme = (rotateScheme == AccessLogConfig.FileHandler.RotateScheme.Enum.SEQUENCE);
        this.compressOnRotation = compressOnRotation;
        init();
    }

    /**
     * Constructs a log handler
     *
     * @param useSequenceNameScheme True to use the sequence file name scheme, false (default) to use the date scheme
     */
    public LogFileHandler(OutputStream out, Formatter formatter, boolean useSequenceNameScheme) {
        this(out, formatter, useSequenceNameScheme, false);
    }

    public LogFileHandler(OutputStream out, Formatter formatter, boolean useSequenceNameScheme, boolean compressOnRotation) {
        super(out, formatter);
        this.useSequenceNameScheme = useSequenceNameScheme;
        this.compressOnRotation = compressOnRotation;
        init();
    }

    private void init() {
        logThread = new LogThread(this);
        logThread.start();
    }

    /**
     * Sends logrecord to file, first rotating file if needed.
     *
     * @param r logrecord to publish
     */
    public void publish(LogRecord r) {
        try {
            logQueue.put(r);
        } catch (InterruptedException e) {
        }
    }

    private void internalPublish(LogRecord r) throws InterruptedException {
        // first check to see if new file needed.
        // if so, use this.internalRotateNow() to do it

        long now = System.currentTimeMillis();
        if (nextRotationTime <= 0) {
            nextRotationTime = getNextRotationTime(now); // lazy initialization
        }
        if (now > nextRotationTime || currentOutputStream == null) {
            internalRotateNow();
        }
        // count records, and publish
        numberOfRecords++;
        super.publish(r);
    }

    /**
     * Assign pattern for generating (rotating) file names.
     *
     * @param pattern See LogFormatter for definition
     */
    public void setFilePattern ( String pattern ) {
        filePattern = pattern;
    }

    /**
     * Assign times for rotating output files.
     *
     * @param timesOfDay in millis, from midnight
     *
     */
    public void setRotationTimes ( long[] timesOfDay ) {
        rotationTimes = timesOfDay;
    }

    /** Assign time for rotating output files
     *
     * @param prescription string form of times, in minutes
     */
    public void setRotationTimes ( String prescription ) {
        setRotationTimes(calcTimesMinutes(prescription));
    }

    /**
     * Find next rotation after specified time.
     *
     * @param now the specified time; if zero, current time is used.
     * @return the next rotation time
     */
    public long getNextRotationTime (long now) {
        if (now <= 0) {
            now = System.currentTimeMillis();
        }
        long nowTod = timeOfDayMillis(now);
        long next = 0;
        for (int i = 0; i<rotationTimes.length; i++) {
            if (nowTod < rotationTimes[i]) {
                next = rotationTimes[i]-nowTod + now;
                break;
            }
        }
        if (next == 0) { // didn't find one -- use 1st time 'tomorrow'
            next = rotationTimes[0]+lengthOfDayMillis-nowTod + now;
        }

        return next;
    }

    private void checkAndCreateDir(String pathname) throws IOException {
      int lastSlash = pathname.lastIndexOf("/");
      if (lastSlash > -1) {
          String pathExcludingFilename = pathname.substring(0, lastSlash);
          File filepath = new File(pathExcludingFilename);
          if (!filepath.exists()) {
            filepath.mkdirs();
          }
      }
    }

    /**
     * Force file rotation now, independent of schedule.
     */
    public void rotateNow () {
        publish(rotateCmd);
    }

    // Throw InterruptedException upwards rather than relying on isInterrupted to stop the thread as
    // isInterrupted() returns false after inerruption in p.waitFor
    private void internalRotateNow() throws InterruptedException {
        // figure out new file name, then
        // use super.setOutputStream to switch to a new file

        String oldFileName = fileName;
        long now = System.currentTimeMillis();
        fileName = LogFormatter.insertDate(filePattern, now);
        super.flush();
        super.close();

        if (useSequenceNameScheme)
            moveCurrentFile();

        try {
            checkAndCreateDir(fileName);
            FileOutputStream os = new FileOutputStream(fileName, true); // append mode, for safety
            super.setOutputStream(os);
            currentOutputStream = os;
        }
        catch (IOException e) {
            throw new RuntimeException("Couldn't open log file '" + fileName + "'", e);
        }

        if ( ! useSequenceNameScheme)
            createSymlinkToCurrentFile();

        numberOfRecords = 0;
        lastRotationTime = now;
        nextRotationTime = 0; //figure it out later (lazy evaluation)
        if (compressOnRotation && (oldFileName != null)) {
            triggerCompression(oldFileName);
        }
    }

    private void triggerCompression(String oldFileName) {
        try {
            Runtime r = Runtime.getRuntime();
            Process p = r.exec(new String[] { "gzip", oldFileName });
            // Detonator pattern: Think of all the fun we can have if gzip isn't what we
            // think it is, if it doesn't return, etc, etc
        } catch (IOException e) {
            // little we can do...
        }
    }

    /** Name files by date - create a symlink with a constant name to the newest file */
    private void createSymlinkToCurrentFile() throws InterruptedException {
        if (symlinkName == null) return;
        File f = new File(fileName);
        File f2 = new File(f.getParent(), symlinkName);
        try {
            Runtime r = Runtime.getRuntime();
            Process p = r.exec(new String[] { "/bin/ln", "-sf", f.getCanonicalPath(), f2.getPath() });
            // Detonator pattern: Think of all the fun we can have if ln isn't what we
            // think it is, if it doesn't return, etc, etc
            p.waitFor();
        } catch (IOException e) {
            // little we can do...
        }
    }

    /**
     * Name the current file to "name.n" where n
     * 1+ the largest integer in existing file names
     */
    private void moveCurrentFile() {
        File file=new File(fileName);
        if ( ! file.exists()) return; // no current file
        File dir=file.getParentFile();
        Pattern logFilePattern=Pattern.compile(".*\\.(\\d+)");
        long largestN=0;
        for (File existingFile : dir.listFiles()) {
            Matcher matcher=logFilePattern.matcher(existingFile.getName());
            if (!matcher.matches()) continue;
            long thisN=Long.parseLong(matcher.group(1));
            if (thisN>largestN)
                largestN=thisN;
        }
        file.renameTo(new File(dir,file.getName() + "." + (largestN + 1)));
    }

    /**
     * @return last time file rotation occurred for this output file
     */
    public long getLastRotationTime () {
        return lastRotationTime;
    }

    /**
     * @return number of records written to this file since last rotation
     */
    public long getNumberRecords () {
        return numberOfRecords;
    }

    /**
     * Calculate rotation times array, given times in minutes, as "0 60 ..."
     *
     */
    public static long[] calcTimesMinutes(String times) {
        ArrayList<Long> list = new ArrayList<>(50);
        int i = 0;
        boolean etc = false;

        while (i < times.length()) {
            if (times.charAt(i) == ' ') { i++; continue; } // skip spaces
            int j = i; // start of string
            i = times.indexOf(' ', i);
            if (i == -1) i = times.length();
            if (times.charAt(j) == '.' && times.substring(j,i).equals("...")) { // ...
                etc = true;
                break;
            }
            list.add(Long.valueOf(times.substring(j,i)));
        }

        int size = list.size();
        long[] longtimes = new long[size];
        for (i = 0; i<size; i++) {
            longtimes[i] = list.get(i).longValue()   // pick up value in minutes past midnight
                           * 60000;                          // and multiply to get millis
        }

        if (etc) { // fill out rest of day, same as final interval
            long endOfDay = 24*60*60*1000;
            long lasttime = longtimes[size-1];
            long interval = lasttime - longtimes[size-2];
            long moreneeded = (endOfDay - lasttime)/interval;
            if (moreneeded > 0) {
                int newsize = size + (int)moreneeded;
                long[] temp = new long[newsize];
                for (i=0; i<size; i++) {
                    temp[i] = longtimes[i];
                }
                while (size < newsize) {
                    lasttime += interval;
                    temp[size++] = lasttime;
                }
                longtimes = temp;
            }
        }

        return longtimes;
    }

    // Support staff :-)
    private static final long lengthOfDayMillis = 24*60*60*1000;  // ? is this close enough ?

    private static final long timeOfDayMillis ( long time ) {
        return time % lengthOfDayMillis;
    }

    public void setSymlinkName(String symlinkName) {
        this.symlinkName = symlinkName;
    }

    /**
     * Flushes all queued messages, interrupts the log thread in this and
     * waits for it to end before returning
     */
    public void shutdown() {
        logThread.interrupt();
        try {
            logThread.join();
        }
        catch (InterruptedException e) {
        }
    }

    /**
     * Only for unit testing. Do not use.
     */
    public String getFileName() {
        return fileName;
    }

}
