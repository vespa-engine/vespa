// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.filedistribution.maintenance;

import com.yahoo.io.IOUtils;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

import static java.nio.file.Files.readAttributes;

/**
 * Removes file references not used since a configured time, but always keeps a certain number of file references
 * even when they are unused (unused is based on last access time for the file).
 *
 * @author hmusum
 */
public class FileDistributionCleanup {

    private static final Logger log = Logger.getLogger(FileDistributionCleanup.class.getName());
    private static final int numberToAlwaysKeep = 20;

    private final Clock clock;

    public FileDistributionCleanup(Clock clock) {
        this.clock = clock;
    }

    public List<String> deleteUnusedFileReferences(File fileReferencesPath,
                                                   Duration keepFileReferencesDuration,
                                                   Set<String> fileReferencesInUse) {
        return deleteUnusedFileReferences(fileReferencesPath,
                                          keepFileReferencesDuration,
                                          numberToAlwaysKeep,
                                          fileReferencesInUse);
    }

    public List<String> deleteUnusedFileReferences(File fileReferencesDir,
                                                   Duration keepFileReferencesDuration,
                                                   int numberToAlwaysKeep,
                                                   Set<String> fileReferencesInUse) {
        if (!fileReferencesDir.isDirectory()) throw new RuntimeException(fileReferencesDir + " is not a directory");

        log.log(Level.FINE, () -> "Keep unused file references for " + keepFileReferencesDuration +
                ", file references in use : " + fileReferencesInUse);
        List<String> fileReferencesDeleted = new ArrayList<>();
        Path fileReferencesPath = fileReferencesDir.toPath();
        try (Stream<String> candidates = sortedUnusedFileReferences(fileReferencesPath, fileReferencesInUse, keepFileReferencesDuration)) {
            final AtomicInteger i = new AtomicInteger(0);
            candidates.forEach(fileReference -> {
                // Do not delete the newest ones
                if (i.incrementAndGet() > numberToAlwaysKeep) {
                    fileReferencesDeleted.add(fileReference);
                    File file = new File(fileReferencesDir, fileReference);
                    if (!IOUtils.recursiveDeleteDir(file))
                        log.log(Level.WARNING, "Could not delete " + file.getAbsolutePath());
                }
            });
        }
        return fileReferencesDeleted;
    }

    // Sorted, newest first
    private Stream<String> sortedUnusedFileReferences(Path fileReferencesPath, Set<String> fileReferencesInUse, Duration keepFileReferences) {
        Instant instant = clock.instant().minus(keepFileReferences);
        return getFileReferencesOnDisk(fileReferencesPath)
                .filter(fileReference -> !fileReferencesInUse.contains(fileReference))
                .filter(fileReference -> isLastFileAccessBefore(new File(fileReferencesPath.toFile(), fileReference), instant))
                .sorted(Comparator.comparing(a -> lastAccessed(new File(fileReferencesPath.toFile(), (String) a))).reversed());
    }

    private boolean isLastFileAccessBefore(File fileReference, Instant instant) {
        return lastAccessed(fileReference).isBefore(instant);
    }

    private Instant lastAccessed(File fileReference) {
        BasicFileAttributes fileAttributes;
        try {
            fileAttributes = readAttributes(fileReference.toPath(), BasicFileAttributes.class);
            return fileAttributes.lastAccessTime().toInstant();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static Stream<String> getFileReferencesOnDisk(Path directory) {
        try {
            return Files.list(directory).map(path -> path.toFile().getName());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

}
