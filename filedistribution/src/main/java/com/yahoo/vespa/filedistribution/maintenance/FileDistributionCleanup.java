// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.filedistribution.maintenance;

import com.yahoo.io.IOUtils;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

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

    public List<String> deleteUnusedFileReferences(File fileReferencesPath,
                                                   Duration keepFileReferencesDuration,
                                                   int numberToAlwaysKeep,
                                                   Set<String> fileReferencesInUse) {
        log.log(Level.FINE, () -> "Keep unused file references for " + keepFileReferencesDuration);
        if (!fileReferencesPath.isDirectory()) throw new RuntimeException(fileReferencesPath + " is not a directory");

        log.log(Level.FINE, () -> "File references in use : " + fileReferencesInUse);

        List<String> candidates = sortedUnusedFileReferences(fileReferencesPath, fileReferencesInUse, keepFileReferencesDuration);
        // Do not delete the newest ones
        List<String> fileReferencesToDelete = candidates.subList(0, Math.max(0, candidates.size() - numberToAlwaysKeep));
        if (fileReferencesToDelete.size() > 0) {
            log.log(Level.FINE, () -> "Will delete file references not in use: " + fileReferencesToDelete);
            fileReferencesToDelete.forEach(fileReference -> {
                File file = new File(fileReferencesPath, fileReference);
                if (!IOUtils.recursiveDeleteDir(file))
                    log.log(Level.WARNING, "Could not delete " + file.getAbsolutePath());
            });
        }
        return fileReferencesToDelete;
    }

    private List<String> sortedUnusedFileReferences(File fileReferencesPath, Set<String> fileReferencesInUse, Duration keepFileReferences) {
        Set<String> fileReferencesOnDisk = getFileReferencesOnDisk(fileReferencesPath);
        log.log(Level.FINE, () -> "File references on disk (in " + fileReferencesPath + "): " + fileReferencesOnDisk);
        Instant instant = clock.instant().minus(keepFileReferences);
        return fileReferencesOnDisk
                .stream()
                .filter(fileReference -> !fileReferencesInUse.contains(fileReference))
                .filter(fileReference -> isLastFileAccessBefore(new File(fileReferencesPath, fileReference), instant))
                .sorted(Comparator.comparing(a -> lastAccessed(new File(fileReferencesPath, a))))
                .collect(Collectors.toList());
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

    /**
     * Returns all files in the given directory, non-recursive.
     */
    public static Set<String> getFileReferencesOnDisk(File directory) {
        Set<String> fileReferencesOnDisk = new HashSet<>();
        File[] filesOnDisk = directory.listFiles();
        if (filesOnDisk != null)
            fileReferencesOnDisk.addAll(Arrays.stream(filesOnDisk).map(File::getName).collect(Collectors.toSet()));
        return fileReferencesOnDisk;
    }

}
