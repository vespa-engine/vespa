// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.logging;

import com.yahoo.compress.ZstdOutputStream;
import com.yahoo.io.NativeIO;
import com.yahoo.log.LogFileDb;
import com.yahoo.protect.Process;
import com.yahoo.yolean.Exceptions;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
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
    private final BlockingQueue<Operation<LOGTYPE>> logQueue;
    final LogThread<LOGTYPE> logThread;

    @FunctionalInterface private interface Pollable<T> { Operation<T> poll() throws InterruptedException; }

    LogFileHandler(Compression compression, int bufferSize, String filePattern, String rotationTimes, String symlinkName,
                   int queueSize, String threadName, LogWriter<LOGTYPE> logWriter) {
        this(compression, bufferSize, filePattern, calcTimesMinutes(rotationTimes), symlinkName, queueSize, threadName, logWriter);
    }

    LogFileHandler(Compression compression, int bufferSize, String filePattern, long[] rotationTimes, String symlinkName,
                   int queueSize, String threadName, LogWriter<LOGTYPE> logWriter) {
        this.logQueue = new LinkedBlockingQueue<>(queueSize);
        this.logThread = new LogThread<>(logWriter, filePattern, compression, bufferSize, rotationTimes, symlinkName, threadName, this::poll);
        this.logThread.start();
    }

    private Operation<LOGTYPE> poll() throws InterruptedException {
        return logQueue.poll(100, TimeUnit.MILLISECONDS);
    }

    /**
     * Sends logrecord to file, first rotating file if needed.
     *
     * @param r logrecord to publish
     */
    public void publish(LOGTYPE r) {
        addOperation(new Operation<>(r));
    }

    void publishAndWait(LOGTYPE r) {
        addOperationAndWait(new Operation<>(r));
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
            logQueue.put(op);
        } catch (InterruptedException e) {
        }
    }

    private void addOperationAndWait(Operation<LOGTYPE> op) {
        try {
            logQueue.put(op);
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
            logThread.executor.shutdownNow();
            logThread.executor.awaitTermination(600, TimeUnit.SECONDS);
            logThread.join();
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
        private final Pollable<LOGTYPE> operationProvider;
        long lastFlush = 0;
        private PageCacheFriendlyFileOutputStream fileOutput = null;
        private long nextRotationTime = 0;
        private final String filePattern;  // default to current directory, ms time stamp
        private volatile String fileName;
        private final LogWriter<LOGTYPE> logWriter;
        private final Compression compression;
        private final int bufferSize;
        private final long[] rotationTimes;
        private final String symlinkName;
        private final ExecutorService executor = createCompressionTaskExecutor();
        private final NativeIO nativeIO = new NativeIO();


        LogThread(LogWriter<LOGTYPE> logWriter,
                  String filePattern,
                  Compression compression,
                  int bufferSize,
                  long[] rotationTimes,
                  String symlinkName,
                  String threadName,
                  Pollable<LOGTYPE> operationProvider) {
            super(threadName);
            setDaemon(true);
            this.logWriter = logWriter;
            this.filePattern = filePattern;
            this.compression = compression;
            this.bufferSize = bufferSize;
            this.rotationTimes = rotationTimes;
            this.symlinkName = (symlinkName != null && !symlinkName.isBlank()) ? symlinkName : null;
            this.operationProvider = operationProvider;
        }

        private static ExecutorService createCompressionTaskExecutor() {
            return Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(runnable, "logfilehandler.compression");
                thread.setDaemon(true);
                thread.setPriority(Thread.MIN_PRIORITY);
                return thread;
            });
        }

        @Override
        public void run() {
            try {
                handleLogOperations();
            } catch (InterruptedException e) {
            } catch (Exception e) {
                Process.logAndDie("Failed storing log records", e);
            }

            internalFlush();
        }

        private void handleLogOperations() throws InterruptedException {
            while (!isInterrupted()) {
                Operation<LOGTYPE> r = operationProvider.poll();
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

        private void internalFlush() {
            try {
                if (fileOutput != null) {
                    fileOutput.flush();
                }
            } catch (IOException e) {
                logger.log(Level.WARNING, "Failed to flush file output: " + Exceptions.toMessageString(e), e);
            }
        }

        private void internalClose() {
            try {
                if (fileOutput != null) {
                    fileOutput.flush();
                    fileOutput.close();
                    fileOutput = null;
                }
            } catch (Exception e) {
                logger.log(Level.WARNING, "Got error while closing log file: " + e.getMessage(), e);
            }
        }

        private void internalPublish(LOGTYPE r) {
            // first check to see if new file needed.
            // if so, use this.internalRotateNow() to do it

            long now = System.currentTimeMillis();
            if (nextRotationTime <= 0) {
                nextRotationTime = getNextRotationTime(now); // lazy initialization
            }
            if (now > nextRotationTime || fileOutput == null) {
                internalRotateNow();
            }
            try {
                logWriter.write(r, fileOutput);
                fileOutput.write('\n');
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

            String oldFileName = fileName;
            long now = System.currentTimeMillis();
            fileName = LogFormatter.insertDate(filePattern, now);
            internalClose();
            try {
                checkAndCreateDir(fileName);
                fileOutput = new PageCacheFriendlyFileOutputStream(nativeIO, Paths.get(fileName), bufferSize);
                LogFileDb.nowLoggingTo(fileName);
            } catch (IOException e) {
                throw new RuntimeException("Couldn't open log file '" + fileName + "'", e);
            }

            if(oldFileName == null) oldFileName = getOldFileNameFromSymlink(); // To compress previous file, if so configured
            createSymlinkToCurrentFile();

            nextRotationTime = 0; //figure it out later (lazy evaluation)
            if ((oldFileName != null)) {
                Path oldFile = Paths.get(oldFileName);
                if (Files.exists(oldFile)) {
                    executor.execute(() -> runCompression(nativeIO, oldFile, compression));
                }
            }
        }


        private static void runCompression(NativeIO nativeIO, Path oldFile, Compression compression) {
            switch (compression) {
                case ZSTD:
                    runCompressionZstd(nativeIO, oldFile);
                    break;
                case GZIP:
                    runCompressionGzip(nativeIO, oldFile);
                    break;
                case NONE:
                    runCompressionNone(nativeIO, oldFile);
                    break;
                default:
                    throw new IllegalArgumentException("Unknown compression " + compression);
            }
        }

        private static void runCompressionNone(NativeIO nativeIO, Path oldFile) {
            nativeIO.dropFileFromCache(oldFile.toFile());
        }

        private static void runCompressionZstd(NativeIO nativeIO, Path oldFile) {
            try {
                Path compressedFile = Paths.get(oldFile.toString() + ".zst");
                int bufferSize = 2*1024*1024;
                try (FileOutputStream fileOut = AtomicFileOutputStream.create(compressedFile);
                     ZstdOutputStream out = new ZstdOutputStream(fileOut, bufferSize);
                     FileInputStream in = new FileInputStream(oldFile.toFile())) {
                    pageFriendlyTransfer(nativeIO, out, fileOut.getFD(), in, bufferSize);
                    out.flush();
                }
                Files.delete(oldFile);
                nativeIO.dropFileFromCache(compressedFile.toFile());
            } catch (IOException e) {
                logger.log(Level.WARNING, "Failed to compress log file with zstd: " + oldFile, e);
            } finally {
                nativeIO.dropFileFromCache(oldFile.toFile());
            }
        }

        private static void runCompressionGzip(NativeIO nativeIO, Path oldFile) {
            try {
                Path gzippedFile = Paths.get(oldFile.toString() + ".gz");
                try (FileOutputStream fileOut = AtomicFileOutputStream.create(gzippedFile);
                     GZIPOutputStream compressor = new GZIPOutputStream(fileOut, 0x100000);
                     FileInputStream inputStream = new FileInputStream(oldFile.toFile())) {
                    pageFriendlyTransfer(nativeIO, compressor, fileOut.getFD(), inputStream, 0x400000);
                    compressor.finish();
                    compressor.flush();
                }
                Files.delete(oldFile);
                nativeIO.dropFileFromCache(gzippedFile.toFile());
            } catch (IOException e) {
                logger.log(Level.WARNING, "Failed to compress log file with gzip: " + oldFile, e);
            } finally {
                nativeIO.dropFileFromCache(oldFile.toFile());
            }
        }

        private static void pageFriendlyTransfer(NativeIO nativeIO, OutputStream out, FileDescriptor outDescriptor, FileInputStream in, int bufferSize) throws IOException {
            int read;
            long totalBytesRead = 0;
            byte[] buffer = new byte[bufferSize];
            while ((read = in.read(buffer)) >= 0) {
                out.write(buffer, 0, read);
                if (read > 0) {
                    nativeIO.dropPartialFileFromCache(in.getFD(), totalBytesRead, read, false);
                    nativeIO.dropPartialFileFromCache(outDescriptor, totalBytesRead, read, false);
                }
                totalBytesRead += read;
            }
        }

        /**
         * Name files by date - create a symlink with a constant name to the newest file
         */
        private void createSymlinkToCurrentFile() {
            if (symlinkName == null) return;
            Path target = Paths.get(fileName);
            Path link = target.resolveSibling(symlinkName);
            try {
                Files.deleteIfExists(link);
                Files.createSymbolicLink(link, target.getFileName());
            } catch (IOException e) {
                logger.log(Level.WARNING, "Failed to create symbolic link to current log file: " + e.getMessage(), e);
            }
        }

        private String getOldFileNameFromSymlink() {
            if(symlinkName == null) return null;
            try {
                return Paths.get(fileName).resolveSibling(symlinkName).toRealPath().toString();
            } catch (IOException e) {
                return null;
            }
        }

        private static final long lengthOfDayMillis = 24 * 60 * 60 * 1000;
        private static long timeOfDayMillis(long time) {
            return time % lengthOfDayMillis;
        }

    }

    private static class Operation<LOGTYPE> {
        enum Type {log, flush, close, rotate}

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

    /** File output stream that signals to kernel to drop previous pages after write */
    private static class PageCacheFriendlyFileOutputStream extends OutputStream {

        private final NativeIO nativeIO;
        private final FileOutputStream fileOut;
        private final BufferedOutputStream bufferedOut;
        private final int bufferSize;
        private long lastDropPosition = 0;

        PageCacheFriendlyFileOutputStream(NativeIO nativeIO, Path file, int bufferSize) throws FileNotFoundException {
            this.nativeIO = nativeIO;
            this.fileOut = new FileOutputStream(file.toFile(), true);
            this.bufferedOut = new BufferedOutputStream(fileOut, bufferSize);
            this.bufferSize = bufferSize;
        }

        @Override public void write(byte[] b) throws IOException { bufferedOut.write(b); }
        @Override public void write(byte[] b, int off, int len) throws IOException { bufferedOut.write(b, off, len); }
        @Override public void write(int b) throws IOException { bufferedOut.write(b); }
        @Override public void close() throws IOException { bufferedOut.close(); }

        @Override
        public void flush() throws IOException {
            bufferedOut.flush();
            long newPos = fileOut.getChannel().position();
            if (newPos >= lastDropPosition + bufferSize) {
                nativeIO.dropPartialFileFromCache(fileOut.getFD(), lastDropPosition, newPos, true);
                lastDropPosition = newPos;
            }
        }
    }

    private static class AtomicFileOutputStream extends FileOutputStream {
        private final Path path;
        private final Path tmpPath;
        private volatile boolean closed = false;

        private AtomicFileOutputStream(Path path, Path tmpPath) throws FileNotFoundException {
            super(tmpPath.toFile());
            this.path = path;
            this.tmpPath = tmpPath;
        }

        @Override
        public synchronized void close() throws IOException {
            super.close();
            if (!closed) {
                Files.move(tmpPath, path, StandardCopyOption.ATOMIC_MOVE);
                closed = true;
            }
        }

        private static AtomicFileOutputStream create(Path path) throws FileNotFoundException {
            return new AtomicFileOutputStream(path, path.resolveSibling("." + path.getFileName() + ".tmp"));
        }
    }
}
