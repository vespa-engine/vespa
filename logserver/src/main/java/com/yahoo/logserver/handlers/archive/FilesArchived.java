// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.logserver.handlers.archive;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.zip.GZIPOutputStream;

import com.yahoo.compress.ZstdOutputStream;
import com.yahoo.io.NativeIO;
import com.yahoo.log.LogFileDb;
import com.yahoo.protect.Process;
import com.yahoo.yolean.Exceptions;

import java.io.BufferedOutputStream;
import java.io.FileDescriptor;
import java.io.FileNotFoundException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;


/**
 * This class holds information about all (log) files contained
 * in the logarchive directory hierarchy.  It also has functionality
 * for compressing log files and deleting older files.
 *
 * @author Arne Juul
 */
public class FilesArchived {
    private static final Logger log = Logger.getLogger(FilesArchived.class.getName());

    /**
     * File instance representing root directory of archive
     */
    private final File root;

    enum Compression {NONE, GZIP, ZSTD}
    private final Compression compression;
    private final NativeIO nativeIO = new NativeIO();

    private final Object mutex = new Object();

    // known-existing files inside the archive directory
    private List<LogFile> knownFiles;

    public static final long compressAfterMillis = 2L * 3600 * 1000;
    private static final long maxAgeDays = 30; // GDPR rules: max 30 days
    private static final long sizeLimit = 30L * (1L << 30); // 30 GB

    private void waitForTrigger(long milliS) throws InterruptedException {
        synchronized (mutex) {
            mutex.wait(milliS);
        }
    }

    private void run() {
        try {
            // Sleep some time before first maintenance, unit test depend on files not being removed immediately
            Thread.sleep(1000);
            while (true) {
                maintenance();
                waitForTrigger(2000);
            }
        } catch (Exception e) {
            // just exit thread on exception, nothing is safe afterwards
            System.err.println("Fatal exception in FilesArchived-maintainer thread: "+e);
        }
    }

    /**
     * Creates an instance of FilesArchive managing the given directory
     */
    public FilesArchived(File rootDir, String zip) {
        this.root = rootDir;
        this.compression = ("zstd".equals(zip)) ? Compression.ZSTD : Compression.GZIP;
        rescan();
        Thread thread = new Thread(this::run);
        thread.setDaemon(true);
        thread.setName("FilesArchived-maintainer");
        thread.start();
    }

    public String toString() {
        return FilesArchived.class.getName() + ": root=" + root;
    }

    public synchronized int highestGen(String prefix) {
        int gen = 0;
        for (LogFile lf : knownFiles) {
            if (prefix.equals(lf.prefix)) {
                gen = Math.max(gen, lf.generation);
            }
        }
        return gen;
    }

    public void triggerMaintenance() {
        synchronized (mutex) {
            mutex.notifyAll();
        }
    }

    synchronized boolean maintenance() {
        boolean action = false;
        rescan();
        if (removeOlderThan(maxAgeDays)) {
            action = true;
            rescan();
        }
        if (compressOldFiles()) {
            action = true;
            rescan();
        }
        long days = maxAgeDays;
        while (tooMuchDiskUsage() && (--days > 1)) {
            if (removeOlderThan(days)) {
                action = true;
                rescan();
            }
        }
        return action;
    }

    private void rescan() {
        knownFiles = scanDir(root);
    }

    boolean tooMuchDiskUsage() {
        long sz = sumFileSizes();
        return sz > sizeLimit;
    }

    private boolean olderThan(LogFile lf, long days, long now) {
        long mtime = lf.path.lastModified();
        long diff = now - mtime;
        return (diff > days * 86400L * 1000L);
    }

    // returns true if any files were removed
    private boolean removeOlderThan(long days) {
        boolean action = false;
        long now = System.currentTimeMillis();
        for (LogFile lf : knownFiles) {
            if (olderThan(lf, days, now)) {
                lf.path.delete();
                log.info("Deleted: "+lf.path);
                action = true;
            }
        }
        return action;
    }

    // returns true if any files were compressed
    private boolean compressOldFiles() {
        long now = System.currentTimeMillis();
        int count = 0;
        for (LogFile lf : knownFiles) {
            // avoid compressing entire archive at once
            if (lf.canCompress(now) && (count++ < 5)) {
                compress(lf.path);
            }
        }
        return count > 0;
    }


    private void compress(File oldFile) {
        switch (compression) {
        case ZSTD:
            runCompressionZstd(nativeIO, oldFile);
            break;
        case GZIP:
            compressGzip(oldFile);
            break;
        case NONE:
            runCompressionNone(nativeIO, oldFile);
            break;
        default:
            throw new IllegalArgumentException("Unknown compression " + compression);
        }
    }

    private void compressGzip(File oldFile) {
        File gzippedFile = new File(oldFile.getPath() + ".gz");
        try (GZIPOutputStream compressor = new GZIPOutputStream(new FileOutputStream(gzippedFile), 0x100000);
             FileInputStream inputStream = new FileInputStream(oldFile))
        {
            long mtime = oldFile.lastModified();
            byte [] buffer = new byte[0x100000];

            for (int read = inputStream.read(buffer); read > 0; read = inputStream.read(buffer)) {
                compressor.write(buffer, 0, read);
            }
            compressor.finish();
            compressor.flush();
            oldFile.delete();
            gzippedFile.setLastModified(mtime);
            log.info("Compressed: "+gzippedFile);
        } catch (IOException e) {
            log.warning("Got '" + e + "' while compressing '" + oldFile.getPath() + "'.");
        }
    }

    private static void runCompressionZstd(NativeIO nativeIO, File oldFile) {
        try {
            Path compressedFile = Paths.get(oldFile.toString() + ".zst");
            int bufferSize = 2*1024*1024;
            long mtime = oldFile.lastModified();
            try (FileOutputStream fileOut = AtomicFileOutputStream.create(compressedFile);
                 ZstdOutputStream out = new ZstdOutputStream(fileOut, bufferSize);
                 FileInputStream in = new FileInputStream(oldFile))
            {
                pageFriendlyTransfer(nativeIO, out, fileOut.getFD(), in, bufferSize);
                out.flush();
            }
            compressedFile.toFile().setLastModified(mtime);
            oldFile.delete();
            nativeIO.dropFileFromCache(compressedFile.toFile());
        } catch (IOException e) {
            log.log(Level.WARNING, "Failed to compress log file with zstd: " + oldFile, e);
        } finally {
            nativeIO.dropFileFromCache(oldFile);
        }
    }

    private static void runCompressionNone(NativeIO nativeIO, File oldFile) {
        nativeIO.dropFileFromCache(oldFile);
    }

    long sumFileSizes() {
        long sum = 0;
        for (LogFile lf : knownFiles) {
            sum += lf.path.length();
        }
        return sum;
    }

    private static final Pattern dateFormatRegexp = Pattern.compile(".*/" +
            "[0-9][0-9][0-9][0-9]/" + // year
            "[0-9][0-9]/" + // month
            "[0-9][0-9]/" + // day
            "[0-9][0-9]-" + // hour
            "[0-9].*"); // generation

    private static List<LogFile> scanDir(File top) {
        List<LogFile> retval = new ArrayList<>();
        String[] names = top.list();
        if (names != null) {
            for (String name : names) {
                File sub = new File(top, name);
                if (sub.isFile()) {
                    String pathName = sub.toString();
                    if (dateFormatRegexp.matcher(pathName).matches()) {
                        retval.add(new LogFile(sub));
                    } else {
                        log.warning("skipping file not matching log archive pattern: "+pathName);
                    }
                } else if (sub.isDirectory()) {
                    retval.addAll(scanDir(sub));
                }
            }
        }
        return retval;
    }

    static class LogFile {
        public final File path;
        public final String prefix;
        public final int generation;
        public final boolean zsuff;

        public boolean canCompress(long now) {
            if (zsuff) return false; // already compressed
            if (! path.isFile()) return false; // not a file
            long diff = now - path.lastModified();
            if (diff < compressAfterMillis) return false; // too new
            return true;
        }

        private static int generationOf(String name) {
            int dash = name.lastIndexOf('-');
            if (dash < 0) return 0;
            String suff = name.substring(dash + 1);
            int r = 0;
            for (char ch : suff.toCharArray()) {
                if (ch >= '0' && ch <= '9') {
                    r *= 10;
                    r += (ch - '0');
                } else {
                    break;
                }
            }
            return r;
        }
        private static String prefixOf(String name) {
            int dash = name.lastIndexOf('-');
            if (dash < 0) return name;
            return name.substring(0, dash);
        }
        private static boolean zSuffix(String name) {
            if (name.endsWith(".gz")) return true;
            if (name.endsWith(".zst")) return true;
            // add other compression suffixes here
            return false;
        }
        public LogFile(File path) {
            String name = path.toString();
            this.path = path;
            this.prefix = prefixOf(name);
            this.generation = generationOf(name);
            this.zsuff = zSuffix(name);
        }
        public String toString() {
            return "FilesArchived.LogFile{name="+path+" prefix="+prefix+" gen="+generation+" z="+zsuff+"}";
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

}
