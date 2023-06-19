// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.logging;

import ai.vespa.validation.Validation;
import com.yahoo.vespa.defaults.Defaults;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.stream.Stream;

/**
 * Spooler that will write an entry to a file and read files that are ready to be sent.
 * Files are written in JSON Lines text file format.
 *
 * @author hmusum
 */
public class Spooler {

    private static final java.util.logging.Logger log = java.util.logging.Logger.getLogger(Spooler.class.getName());
    private static final Path defaultSpoolPath = Path.of(Defaults.getDefaults().underVespaHome("var/spool/vespa/events"));
    private static final Comparator<File> ordering = new TimestampCompare();
    private static final int defaultMaxEntriesPerFile = 100;
    // Maximum delay between first write to a file and when we should close file and move it for further processing
    static final Duration maxDelayAfterFirstWrite = Duration.ofSeconds(5);
    private static final int maxFilesToRead = 50;

    private Path processingPath;
    private Path readyPath;
    private Path failuresPath;
    private Path successesPath;

    // Number of next entry to be written to the current file
    AtomicInteger entryCounter = new AtomicInteger(0);
    AtomicLong fileNameBase = new AtomicLong(0);
    AtomicInteger fileCounter = new AtomicInteger(0);

    private final Path spoolPath;
    private final int maxEntriesPerFile;
    private final Clock clock;
    private final AtomicReference<Instant> firstWriteTimestamp = new AtomicReference<>();
    private final boolean keepSuccessFiles;
    private final int maxFailures;
    private final Map<File, Integer> failures = new ConcurrentHashMap<>();

    public Spooler(Clock clock) {
        this(clock, false, 1000);
    }

    public Spooler(Clock clock, boolean keepSuccessFiles, int maxFailures) {
        this(defaultSpoolPath, defaultMaxEntriesPerFile, clock, keepSuccessFiles, maxFailures);
    }

    // Note: Needs to be public, used in system tests
    public Spooler(Path spoolPath, int maxEntriesPerFile, Clock clock, boolean keepSuccessFiles, int maxFailures) {
        this.spoolPath = spoolPath;
        this.maxEntriesPerFile = maxEntriesPerFile;
        this.clock = clock;
        this.fileNameBase.set(newFileNameBase(clock));
        this.keepSuccessFiles = keepSuccessFiles;
        this.maxFailures = maxFailures;
        firstWriteTimestamp.set(Instant.EPOCH);
        createDirs(spoolPath);
    }

    void write(LoggerEntry entry) {
        writeEntry(entry);
    }

    public void processFiles(Function<LoggerEntry, Boolean> transport) throws IOException {
        List<Path> files = listFilesInPath(readyPath);
        if (files.size() == 0) {
            log.log(Level.FINEST, "No files in ready path " + readyPath.toFile().getAbsolutePath());
            return;
        }
        log.log(Level.FINE, "Files in ready path: " + files.size());

        List<File> fileList = getFiles(files);
        if ( ! fileList.isEmpty()) {
            processFiles(fileList, transport);
        }

    }

    List<Path> listFilesInPath(Path path) throws IOException {
        List<Path> files;
        try (Stream<Path> stream = Files.list(path)) {
            files = stream.toList();
            // TODO: Or check if stream is empty
        } catch (NoSuchFileException e) {
            return List.of(); // No files, this is OK
        }
        return files;
    }

    public void processFiles(List<File> files, Function<LoggerEntry, Boolean> transport) {
        for (File f : files) {
            log.log(Level.FINE, "Processing file " + f);
            boolean success = false;
            try {
                List<String> lines = Files.readAllLines(f.toPath());
                for (String line : lines) {
                    LoggerEntry entry = LoggerEntry.deserialize(line);
                    log.log(Level.FINE, "Read entry " + entry + " from " + f);
                    success = transport.apply(entry);
                    if (! success) {
                        throw new RuntimeException("Unable to process file " + f + ": unsuccessful call to transport() for " + entry);
                    }
                }
                failures.remove(f);
            } catch (Exception e) {
                handleFailure(f);
            } finally {
                if (success) {
                    if (keepSuccessFiles)
                        moveProcessedFile(f, successesPath);
                    else
                        try {
                            Files.delete(f.toPath());
                        } catch (IOException e) {
                            log.log(Level.WARNING, "Unable to delete file " + f, e);
                        }
                }
            }
        }
    }

    private void handleFailure(File file) {
        failures.putIfAbsent(file, 0);
        var failCount = failures.compute(file, (f, count) -> count + 1);
        if (failCount > maxFailures) {
            log.log(Level.WARNING, "Unable to process file " + file + " after trying " + maxFailures + " times, moving it to " + failuresPath);
            moveProcessedFile(file, failuresPath);
        } else {
            log.log(Level.INFO, "Unable to process file " + file + " after trying " + maxFailures + " times, will retry");
        }
    }

    private void moveProcessedFile(File f, Path path) {
        Path file = f.toPath();
        Path target = spoolPath.resolve(path).resolve(f.toPath().relativize(file)).resolve(f.getName());
        try {
            Files.move(file, target);
        } catch (IOException e) {
            log.log(Level.SEVERE, "Unable to move processed file " + file + " to " + target, e);
        }
    }

    public Path processingPath() { return processingPath; }
    public Path readyPath() { return readyPath; }
    public Path successesPath() { return successesPath; }
    public Path failuresPath() { return failuresPath; }

    List<File> getFiles(List<Path> files) {
        Validation.requireAtLeast(maxFilesToRead, "count must be a positive number", 1);
        List<File> fileList = new ArrayList<>();

        for (Path p : files) {
            File f = p.toFile();
            if (!f.isDirectory()) {
                fileList.add(f);
            }

            // Grab only some files
            if (fileList.size() > maxFilesToRead) {
                break;
            }
        }

        fileList.sort(ordering);
        return fileList;
    }

    private void writeEntry(LoggerEntry entry) {
        String fileName = currentFileName();
        Path file = spoolPath.resolve(processingPath).resolve(fileName);
        try {
            log.log(Level.FINE, "Writing entry " + entryCounter.get() + " (" + entry.serialize() + ") to file " + fileName);
            Files.writeString(file, entry.serialize() + "\n", StandardOpenOption.WRITE, StandardOpenOption.APPEND, StandardOpenOption.CREATE);
            firstWriteTimestamp.compareAndExchange(Instant.EPOCH, clock.instant());
            entryCounter.incrementAndGet();
            switchFileIfNeeded(file, fileName);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    void switchFileIfNeeded() throws IOException {
        String fileName = currentFileName();
        Path file = spoolPath.resolve(processingPath).resolve(fileName);
        switchFileIfNeeded(file, fileName);
    }

    Map<File, Integer> failures() { return failures; }

    private synchronized void switchFileIfNeeded(Path file, String fileName) throws IOException {
        if (file.toFile().exists()
                && (entryCounter.get() >= maxEntriesPerFile || firstWriteTimestamp.get().plus(maxDelayAfterFirstWrite).isBefore(clock.instant()))) {
            Path target = spoolPath.resolve(readyPath).resolve(file.relativize(file)).resolve(fileName);
            log.log(Level.INFO, "Finished writing file " + file + " with " + entryCounter.get() + " entries, moving it to " + target);
            Files.move(file, target);
            entryCounter.set(1);
            fileCounter.incrementAndGet();
            fileNameBase.set(newFileNameBase(clock));
            firstWriteTimestamp.set(Instant.EPOCH);
        }
    }

    synchronized String currentFileName() {
        return fileNameBase.get() + "-" + fileCounter;
    }

    // Need to use a unique file name, see also currentFileName()
    private static long newFileNameBase(Clock clock) {
        return clock.instant().getEpochSecond();
    }

    private void createDirs(Path spoolerPath) {
        processingPath = createDir(spoolerPath.resolve("processing"));
        readyPath = createDir(spoolerPath.resolve("ready"));
        failuresPath = createDir(spoolerPath.resolve("failures"));
        successesPath = createDir(spoolerPath.resolve("successes"));
    }

    private static Path createDir(Path path) {
        File file = path.toFile();
        if (file.exists() && file.canRead() && file.canWrite()) {
            log.log(Level.INFO, "Directory " + path + " already exists");
        } else if (file.mkdirs()) {
            log.log(Level.FINE, "Created " + path);
        } else {
            log.log(Level.WARNING, "Could not create " + path + ", please check permissions");
        }
        return path;
    }

    private static class TimestampCompare implements Comparator<File> {
        public int compare(File a, File b) {
            return (int) (a.lastModified() - b.lastModified());
        }
    }

}
