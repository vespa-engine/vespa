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
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.stream.Stream;

/**
 * Spooler that will write an entry to a file and read files that are ready to be sent
 *
 * @author hmusum
 */
public class Spooler {

    private static final java.util.logging.Logger log = java.util.logging.Logger.getLogger(Spooler.class.getName());
    private static final Path defaultSpoolPath = Path.of(Defaults.getDefaults().underVespaHome("var/spool/vespa/events"));
    private static final Comparator<File> ordering = new TimestampCompare();

    private Path processingPath;
    private Path readyPath;
    private Path failuresPath;
    private Path successesPath;

    AtomicInteger fileCounter = new AtomicInteger(1);

    private final Path spoolPath;

    public Spooler() {
        this(defaultSpoolPath);
    }

    public Spooler(Path spoolPath) {
        this.spoolPath = spoolPath;
        createDirs(spoolPath);
    }

    void write(LoggerEntry entry) {
        writeEntry(entry);
    }

    public void processFiles(Function<LoggerEntry, Boolean> transport) throws IOException {
        List<Path> files = listFilesInPath(readyPath);
        if (files.size() == 0) {
            log.log(Level.INFO, "No files in ready path " + readyPath.toFile().getAbsolutePath());
            return;
        }
        log.log(Level.FINE, "Files in ready path: " + files.size());

        List<File> fileList = getFiles(files, 50); // TODO
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

    public void processFiles(List<File> files, Function<LoggerEntry, Boolean> transport) throws IOException {
        for (File f : files) {
            log.log(Level.INFO, "Found file " + f);
            var content = Files.readAllBytes(f.toPath());
            var entry = LoggerEntry.fromJson(content);

            if (transport.apply(entry)) {
                Path file = f.toPath();
                Path target = spoolPath.resolve(successesPath).resolve(f.toPath().relativize(file)).resolve(f.getName());
                Files.move(file, target);
            }
        }
    }

    public Path processingPath() { return processingPath; }
    public Path readyPath() { return readyPath; }
    public Path successesPath() { return successesPath; }
    public Path failuresPath() { return failuresPath; }

    List<File> getDirectories(File[] files) {
        List<File> fileList = new ArrayList<>();

        for (File f : files) {
            if (f.isDirectory()) {
                fileList.add(f);
            }
        }

        Collections.sort(fileList);
        return fileList;
    }

    List<File> getFiles(List<Path> files, int count) {
        Validation.requireAtLeast(count, "count must be a positive number", 1);
        List<File> fileList = new ArrayList<>();

        for (Path p : files) {
            File f = p.toFile();
            if (!f.isDirectory()) {
                fileList.add(f);
            }

            // Grab only some files
            if (fileList.size() > count) {
                break;
            }
        }

        fileList.sort(ordering);
        return fileList;
    }

    private void writeEntry(LoggerEntry entry) {
        String fileName = String.valueOf(fileCounter);
        Path file = spoolPath.resolve(processingPath).resolve(fileName);
        try {
            Files.writeString(file, entry.toJson(), StandardOpenOption.WRITE, StandardOpenOption.APPEND, StandardOpenOption.CREATE);
            Path target = spoolPath.resolve(readyPath).resolve(file.relativize(file)).resolve(fileName);
            log.log(Level.INFO, "Moving file from " + file + " to " + target);
            Files.move(file, target);
            fileCounter.addAndGet(1);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
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
