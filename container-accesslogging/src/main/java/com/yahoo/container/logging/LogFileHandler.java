// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.logging;

import com.yahoo.concurrent.ThreadFactoryFactory;
import com.yahoo.container.core.AccessLogConfig;
import com.yahoo.io.NativeIO;
import com.yahoo.log.LogFileDb;
import com.yahoo.system.ProcessExecuter;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.StreamHandler;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPOutputStream;

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

    private final static Logger logger = Logger.getLogger(LogFileHandler.class.getName());
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
    private LogRecord rotateCmd = new LogRecord(Level.SEVERE, "rotateNow");
    private ExecutorService executor = Executors.newCachedThreadPool(ThreadFactoryFactory.getDaemonThreadFactory("logfilehandler.compression"));

    static private class LogThread extends Thread {
        LogFileHandler logFileHandler;
        long lastFlush = 0;
        LogThread(LogFileHandler logFile) {
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
    private final LogThread logThread;

    LogFileHandler() {
        this(false);
    }

    LogFileHandler(boolean compressOnRotation)
    {
        super();
        this.compressOnRotation = compressOnRotation;
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
    void setFilePattern ( String pattern ) {
        filePattern = pattern;
    }

    /**
     * Assign times for rotating output files.
     *
     * @param timesOfDay in millis, from midnight
     *
     */
    void setRotationTimes ( long[] timesOfDay ) {
        rotationTimes = timesOfDay;
    }

    /** Assign time for rotating output files
     *
     * @param prescription string form of times, in minutes
     */
    void setRotationTimes ( String prescription ) {
        setRotationTimes(calcTimesMinutes(prescription));
    }

    /**
     * Find next rotation after specified time.
     *
     * @param now the specified time; if zero, current time is used.
     * @return the next rotation time
     */
    long getNextRotationTime (long now) {
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

    void waitDrained() {
        while(! logQueue.isEmpty()) {
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
            }
        }
        super.flush();
    }

    private void checkAndCreateDir(String pathname) {
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
    void rotateNow () {
        publish(rotateCmd);
    }

    // Throw InterruptedException upwards rather than relying on isInterrupted to stop the thread as
    // isInterrupted() returns false after interruption in p.waitFor
    private void internalRotateNow() throws InterruptedException {
        // figure out new file name, then
        // use super.setOutputStream to switch to a new file

        String oldFileName = fileName;
        long now = System.currentTimeMillis();
        fileName = LogFormatter.insertDate(filePattern, now);
        super.flush();
        super.close();

        try {
            checkAndCreateDir(fileName);
            FileOutputStream os = new FileOutputStream(fileName, true); // append mode, for safety
            super.setOutputStream(os);
            currentOutputStream = os;
            LogFileDb.nowLoggingTo(fileName);
        }
        catch (IOException e) {
            throw new RuntimeException("Couldn't open log file '" + fileName + "'", e);
        }

        createSymlinkToCurrentFile();

        numberOfRecords = 0;
        lastRotationTime = now;
        nextRotationTime = 0; //figure it out later (lazy evaluation)
        if ((oldFileName != null)) {
            File oldFile = new File(oldFileName);
            if (oldFile.exists()) {
                if (compressOnRotation) {
                    executor.execute(() -> runCompression(oldFile));
                } else {
                    NativeIO nativeIO = new NativeIO();
                    nativeIO.dropFileFromCache(oldFile);
                }
            }
        }
    }

    private void runCompression(File oldFile) {
        File gzippedFile = new File(oldFile.getPath() + ".gz");
        try {
            GZIPOutputStream compressor = new GZIPOutputStream(new FileOutputStream(gzippedFile), 0x100000);
            FileInputStream inputStream = new FileInputStream(oldFile);
            byte [] buffer = new byte[0x100000];

            for (int read = inputStream.read(buffer); read > 0; read = inputStream.read(buffer)) {
                compressor.write(buffer, 0, read);
            }
            inputStream.close();
            compressor.finish();
            compressor.flush();
            compressor.close();

            NativeIO nativeIO = new NativeIO();
            nativeIO.dropFileFromCache(oldFile); // Drop from cache in case somebody else has a reference to it preventing from dying quickly.
            oldFile.delete();
            nativeIO.dropFileFromCache(gzippedFile);
        } catch (IOException e) {
            logger.warning("Got '" + e + "' while compressing '" + oldFile.getPath() + "'.");
        }
    }

    /** Name files by date - create a symlink with a constant name to the newest file */
    private void createSymlinkToCurrentFile() {
        if (symlinkName == null) return;
        File f = new File(fileName);
        File f2 = new File(f.getParent(), symlinkName);
        String canonicalPath;
        try {
            canonicalPath = f.getCanonicalPath();
        } catch (IOException e) {
            logger.warning("Got '" + e + "' while doing f.getCanonicalPath() on file '" + f.getPath() + "'.");
            return;
        }
        String [] cmd = new String[]{"/bin/ln", "-sf", canonicalPath, f2.getPath()};
        try {
            int retval = new ProcessExecuter().exec(cmd).getFirst();
            // Detonator pattern: Think of all the fun we can have if ln isn't what we
            // think it is, if it doesn't return, etc, etc
            if (retval != 0) {
                logger.warning("Command '" + Arrays.toString(cmd) + "' + failed with exitcode=" + retval);
            }
        } catch (IOException e) {
            logger.warning("Got '" + e + "' while doing'" + Arrays.toString(cmd) + "'.");
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
        File newFn = new File(dir, file.getName() + "." + (largestN + 1));
        LogFileDb.nowLoggingTo(newFn.getAbsolutePath());
        file.renameTo(newFn);
    }

    /**
     * Calculate rotation times array, given times in minutes, as "0 60 ..."
     *
     */
    private static long[] calcTimesMinutes(String times) {
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
            longtimes[i] = list.get(i)   // pick up value in minutes past midnight
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

    private static long timeOfDayMillis ( long time ) {
        return time % lengthOfDayMillis;
    }

    void setSymlinkName(String symlinkName) {
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
            executor.shutdown();
            executor.awaitTermination(600, TimeUnit.SECONDS);
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
