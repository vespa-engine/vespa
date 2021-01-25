// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.logging;

import com.yahoo.compress.ZstdOuputStream;
import com.yahoo.concurrent.ThreadFactoryFactory;
import com.yahoo.io.NativeIO;
import com.yahoo.log.LogFileDb;
import com.yahoo.system.ProcessExecuter;
import com.yahoo.yolean.Exceptions;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.GZIPOutputStream;

/**
 * Implements log file naming/rotating logic for container logs.
 *
 * @author Bob Travis
 * @author bjorncs
 */
class LogFileHandler <LOGTYPE> {

    enum Compression {NONE, GZIP, ZSTD}

    private final static Logger logger = Logger.getLogger(LogFileHandler.class.getName());

    final LogThread<LOGTYPE> logThread;

    LogFileHandler(Compression compression, String filePattern, String rotationTimes, String symlinkName, LogWriter<LOGTYPE> logWriter) {
        this(compression, filePattern, calcTimesMinutes(rotationTimes), symlinkName, logWriter);
    }

    LogFileHandler(
            Compression compression,
            String filePattern,
            long[] rotationTimes,
            String symlinkName,
            LogWriter<LOGTYPE> logWriter) {
        this.logThread = new LogThread<>(logWriter, filePattern, compression, rotationTimes, symlinkName);
        this.logThread.start();
    }


    /**
     * Sends logrecord to file, first rotating file if needed.
     *
     * @param r logrecord to publish
     */
    public void publish(LOGTYPE r) {
        addOperation(new Operation<>(r));
    }

    public void flush() {
        addOperationAndWait(new Operation<>(Operation.Type.flush));
    }

    /**
     * Force file rotation now, independent of schedule.
     */
    void rotateNow() {
        addOperationAndWait(new Operation<>(Operation.Type.rotate));
    }

    public void close() {
        addOperationAndWait(new Operation<>(Operation.Type.close));
    }

    private void addOperation(Operation<LOGTYPE> op) {
        try {
            logThread.logQueue.put(op);
        } catch (InterruptedException e) {
        }
    }

    private void addOperationAndWait(Operation<LOGTYPE> op) {
        try {
            logThread.logQueue.put(op);
            op.countDownLatch.await();
        } catch (InterruptedException e) {
        }
    }

    /**
     * Flushes all queued messages, interrupts the log thread in this and
     * waits for it to end before returning
     */
    void shutdown() {
        logThread.interrupt();
        try {
            logThread.join();
            logThread.executor.shutdown();
            logThread.executor.awaitTermination(600, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
        }
    }

    /**
     * Calculate rotation times array, given times in minutes, as "0 60 ..."
     */
    private static long[] calcTimesMinutes(String times) {
        ArrayList<Long> list = new ArrayList<>(50);
        int i = 0;
        boolean etc = false;

        while (i < times.length()) {
            if (times.charAt(i) == ' ') {
                i++;
                continue;
            } // skip spaces
            int j = i; // start of string
            i = times.indexOf(' ', i);
            if (i == -1) i = times.length();
            if (times.charAt(j) == '.' && times.substring(j, i).equals("...")) { // ...
                etc = true;
                break;
            }
            list.add(Long.valueOf(times.substring(j, i)));
        }

        int size = list.size();
        long[] longtimes = new long[size];
        for (i = 0; i < size; i++) {
            longtimes[i] = list.get(i)   // pick up value in minutes past midnight
                           * 60000;                          // and multiply to get millis
        }

        if (etc) { // fill out rest of day, same as final interval
            long endOfDay = 24 * 60 * 60 * 1000;
            long lasttime = longtimes[size - 1];
            long interval = lasttime - longtimes[size - 2];
            long moreneeded = (endOfDay - lasttime) / interval;
            if (moreneeded > 0) {
                int newsize = size + (int) moreneeded;
                long[] temp = new long[newsize];
                for (i = 0; i < size; i++) {
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

    /**
     * Only for unit testing. Do not use.
     */
    String getFileName() {
        return logThread.fileName;
    }

    /**
     * Handle logging and file operations
     */
    static class LogThread<LOGTYPE> extends Thread {
        long lastFlush = 0;
        private FileOutputStream currentOutputStream = null;
        private long nextRotationTime = 0;
        private final String filePattern;  // default to current directory, ms time stamp
        private String fileName;
        private long lastDropPosition = 0;
        private final LogWriter<LOGTYPE> logWriter;
        private final ArrayBlockingQueue<Operation<LOGTYPE>> logQueue = new ArrayBlockingQueue<>(100000);
        private final Compression compression;
        private final long[] rotationTimes;
        private final String symlinkName;
        private final ExecutorService executor = Executors.newCachedThreadPool(ThreadFactoryFactory.getDaemonThreadFactory("logfilehandler.compression"));
        private final NativeIO nativeIO = new NativeIO();


        LogThread(LogWriter<LOGTYPE> logWriter, String filePattern, Compression compression, long[] rotationTimes, String symlinkName) {
            super("Logger");
            setDaemon(true);
            this.logWriter = logWriter;
            this.filePattern = filePattern;
            this.compression = compression;
            this.rotationTimes = rotationTimes;
            this.symlinkName = (symlinkName != null && !symlinkName.isBlank()) ? symlinkName : null;
        }

        @Override
        public void run() {
            try {
                storeLogRecords();
            } catch (InterruptedException e) {
            } catch (Exception e) {
                com.yahoo.protect.Process.logAndDie("Failed storing log records", e);
            }

            internalFlush();
        }

        private void storeLogRecords() throws InterruptedException {
            while (!isInterrupted()) {
                Operation<LOGTYPE> r = logQueue.poll(100, TimeUnit.MILLISECONDS);
                if (r != null) {
                    if (r.type == Operation.Type.flush) {
                        internalFlush();
                    } else if (r.type == Operation.Type.close) {
                        internalClose();
                    } else if (r.type == Operation.Type.rotate) {
                        internalRotateNow();
                        lastFlush = System.nanoTime();
                    } else if (r.type == Operation.Type.log) {
                        internalPublish(r.log.get());
                        flushIfOld(3, TimeUnit.SECONDS);
                    }
                    r.countDownLatch.countDown();
                } else {
                    flushIfOld(100, TimeUnit.MILLISECONDS);
                }
            }
        }

        private void flushIfOld(long age, TimeUnit unit) {
            long now = System.nanoTime();
            if (TimeUnit.NANOSECONDS.toMillis(now - lastFlush) > unit.toMillis(age)) {
                internalFlush();
                lastFlush = now;
            }
        }

        private synchronized void internalFlush() {
            try {
                FileOutputStream currentOut = this.currentOutputStream;
                if (currentOut != null) {
                    if (compression == Compression.GZIP) {
                        long newPos = currentOut.getChannel().position();
                        if (newPos > lastDropPosition + 102400) {
                            nativeIO.dropPartialFileFromCache(currentOut.getFD(), lastDropPosition, newPos, true);
                            lastDropPosition = newPos;
                        }
                    } else {
                        currentOut.flush();
                    }
                }
            } catch (IOException e) {
                logger.warning("Failed dropping from cache : " + Exceptions.toMessageString(e));
            }
        }

        private void internalClose() {
            try {
                internalFlush();
                FileOutputStream currentOut = this.currentOutputStream;
                if (currentOut != null) currentOut.close();
            } catch (Exception e) {
                logger.log(Level.WARNING, "Got error while closing log file", e);
            }
        }

        private void internalPublish(LOGTYPE r) {
            // first check to see if new file needed.
            // if so, use this.internalRotateNow() to do it

            long now = System.currentTimeMillis();
            if (nextRotationTime <= 0) {
                nextRotationTime = getNextRotationTime(now); // lazy initialization
            }
            if (now > nextRotationTime || currentOutputStream == null) {
                internalRotateNow();
            }
            try {
                FileOutputStream out = this.currentOutputStream;
                logWriter.write(r, out);
                out.write('\n');
            } catch (IOException e) {
                logger.warning("Failed writing log record: " + Exceptions.toMessageString(e));
            }
        }

        /**
         * Find next rotation after specified time.
         *
         * @param now the specified time; if zero, current time is used.
         * @return the next rotation time
         */
        long getNextRotationTime(long now) {
            if (now <= 0) {
                now = System.currentTimeMillis();
            }
            long nowTod = timeOfDayMillis(now);
            long next = 0;
            for (long rotationTime : rotationTimes) {
                if (nowTod < rotationTime) {
                    next = rotationTime - nowTod + now;
                    break;
                }
            }
            if (next == 0) { // didn't find one -- use 1st time 'tomorrow'
                next = rotationTimes[0] + lengthOfDayMillis - nowTod + now;
            }

            return next;
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


        // Throw InterruptedException upwards rather than relying on isInterrupted to stop the thread as
        // isInterrupted() returns false after interruption in p.waitFor
        private void internalRotateNow() {
            // figure out new file name, then
            // use super.setOutputStream to switch to a new file

            String oldFileName = fileName;
            long now = System.currentTimeMillis();
            fileName = LogFormatter.insertDate(filePattern, now);
            internalFlush();

            try {
                checkAndCreateDir(fileName);
                FileOutputStream os = new FileOutputStream(fileName, true); // append mode, for safety
                currentOutputStream = os;
                lastDropPosition = 0;
                LogFileDb.nowLoggingTo(fileName);
            } catch (IOException e) {
                throw new RuntimeException("Couldn't open log file '" + fileName + "'", e);
            }

            createSymlinkToCurrentFile();

            nextRotationTime = 0; //figure it out later (lazy evaluation)
            if ((oldFileName != null)) {
                File oldFile = new File(oldFileName);
                if (oldFile.exists()) {
                    if (compression != Compression.NONE) {
                        executor.execute(() -> runCompression(oldFile, compression));
                    } else {
                        nativeIO.dropFileFromCache(oldFile);
                    }
                }
            }
        }


        private static void runCompression(File oldFile, Compression compression) {
            switch (compression) {
                case ZSTD:
                    runCompressionZstd(oldFile.toPath());
                    break;
                case GZIP:
                    runCompressionGzip(oldFile);
                    break;
                default:
                    throw new IllegalArgumentException("Unknown compression " + compression);
            }
        }

        private static void runCompressionZstd(Path oldFile) {
            try {
                Path compressedFile = Paths.get(oldFile.toString() + ".zst");
                Files.createFile(compressedFile);
                int bufferSize = 0x400000; // 4M
                byte[] buffer = new byte[bufferSize];
                try (ZstdOuputStream out = new ZstdOuputStream(Files.newOutputStream(compressedFile), bufferSize);
                     InputStream in = Files.newInputStream(oldFile)) {
                    int read;
                    while ((read = in.read(buffer)) >= 0) {
                        out.write(buffer, 0, read);
                    }
                    out.flush();
                }
                Files.delete(oldFile);
            } catch (IOException e) {
                logger.log(Level.WARNING, "Failed to compress log file with zstd: " + oldFile, e);
            }
        }

        private static void runCompressionGzip(File oldFile) {
            File gzippedFile = new File(oldFile.getPath() + ".gz");
            try (GZIPOutputStream compressor = new GZIPOutputStream(new FileOutputStream(gzippedFile), 0x100000);
                 FileInputStream inputStream = new FileInputStream(oldFile)) {
                byte[] buffer = new byte[0x400000]; // 4M buffer

                long totalBytesRead = 0;
                NativeIO nativeIO = new NativeIO();
                for (int read = inputStream.read(buffer); read > 0; read = inputStream.read(buffer)) {
                    compressor.write(buffer, 0, read);
                    nativeIO.dropPartialFileFromCache(inputStream.getFD(), totalBytesRead, read, false);
                    totalBytesRead += read;
                }
                compressor.finish();
                compressor.flush();

                oldFile.delete();
                nativeIO.dropFileFromCache(gzippedFile);
            } catch (IOException e) {
                logger.warning("Got '" + e + "' while compressing '" + oldFile.getPath() + "'.");
            }
        }

        /**
         * Name files by date - create a symlink with a constant name to the newest file
         */
        private void createSymlinkToCurrentFile() {
            if (symlinkName == null) return;
            File f = new File(fileName);
            File f2 = new File(f.getParent(), symlinkName);
            String[] cmd = new String[]{"/bin/ln", "-sf", f.getName(), f2.getPath()};
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

        // Support staff :-)
        private static final long lengthOfDayMillis = 24 * 60 * 60 * 1000;  // ? is this close enough ?

        private static long timeOfDayMillis(long time) {
            return time % lengthOfDayMillis;
        }

    }

    private static class Operation<LOGTYPE> {
        enum Type {log, flush, close, rotate}

        ;

        final Type type;

        final Optional<LOGTYPE> log;
        final CountDownLatch countDownLatch = new CountDownLatch(1);

        Operation(Type type) {
            this(type, Optional.empty());
        }

        Operation(LOGTYPE log) {
            this(Type.log, Optional.of(log));
        }

        private Operation(Type type, Optional<LOGTYPE> log) {
            this.type = type;
            this.log = log;
        }
    }
}

