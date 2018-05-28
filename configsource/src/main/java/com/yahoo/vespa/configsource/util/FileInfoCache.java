// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.configsource.util;

import com.yahoo.log.LogLevel;

import javax.annotation.concurrent.ThreadSafe;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * A {@code FileInfoCache} provides an up-to-date FileInfo with the underlying file.
 *
 * @author hakon
 */
@ThreadSafe
public class FileInfoCache {
    private static final Logger logger = Logger.getLogger(FileInfoCache.class.getName());

    private final FilesApi filesApi;
    private final Path path;

    private volatile Optional<FileInfo> lastFileInfo = Optional.empty();

    public FileInfoCache(Path path) {
        this(path, new FilesApi());
    }

    /** For testing only. */
    public FileInfoCache(Path path, FilesApi filesApi) {
        this.filesApi = filesApi;
        this.path = path;
    }

    public Path getPath() {
        return path;
    }

    /**
     * Ensure FileInfo matches fileUpdate (and get) FileInfo if deemed necessary.
     *
     * @throws UncheckedIOException on IO error (not that missing file is NOT an error)
     */
    public Optional<FileInfo> syncAndGet() {
        Optional<FileInfo> snapshot = lastFileInfo;
        Optional<FileInfo> newIfChanged;
        try {
            newIfChanged = syncAndGetIfChanged(snapshot);
        } catch (RuntimeException e) {
            logger.log(LogLevel.WARNING, "Failed to update file data for " + path, e);
            // Avoid modifying lastFileInfo on error.
            return snapshot;
        }

        if (newIfChanged.isPresent()) {
            lastFileInfo = newIfChanged;
            return newIfChanged;
        } else {
            return snapshot;
        }
    }

    private Optional<FileInfo> syncAndGetIfChanged(Optional<FileInfo> snapshot) {
        if (snapshot.isPresent()) {
            return syncAndGetIfChanged(snapshot.get());
        } else if (!filesApi.exists(path)) {
            return Optional.of(FileInfo.forMissingFile(path));
        } else {
            return Optional.of(getFileInfoForExisting());
        }
    }

    /**
     * Get FileInfo if it has changed since lastFileInfo.
     *
     * @throws RuntimeException (or subclass of) on error, typically UncheckedIOException.
     */
    private Optional<FileInfo> syncAndGetIfChanged(FileInfo lastFileInfo) {
        // Optimizations:
        //  1. If the file did exist last time, then assume it still exist without changes.
        //     We can then do 1 file attribute read and report no change if last modified time
        //     is unchanged.
        //  2. If the file did NOT exist last time, then assume it will still not exist now.
        //     We can then do 1 file check and report no change.
        //
        // Thus in the normal case, of no change, both of these make 1 system call hitting
        // the kernel in-memory file metadata.

        if (lastFileInfo.exists()) {
            Optional<Instant> lastModifiedTime = getLastModifiedTimeIfExists();
            if (!lastModifiedTime.isPresent()) {
                // File no longer exists.
                return createForMissingFile(lastFileInfo);
            }

            // Optimization 1: If the file did exist last time...
            if (!lastModifiedTime.get().isAfter(lastFileInfo.lastModifiedTime())) {
                return Optional.empty();
            }

            Optional<byte[]> content = readContentIfExists();
            if (!content.isPresent()) {
                // File no longer exists.
                return createForMissingFile(lastFileInfo);
            }

            // Check whether the content is identical.
            if (content.get().equals(lastFileInfo.content())) {
                return Optional.empty();
            }

            return Optional.of(FileInfo.forExistingFile(path, lastModifiedTime.get(), content.get()));
        } else if (!filesApi.exists(lastFileInfo.path())) {
            // Optimization 2: If the file did NOT exist last time...
            return Optional.empty();
        } else {
            return Optional.of(getFileInfoForExisting());
        }
    }

    /**
     * Gets FileInfo of path. Optimized for the file existing.
     *
     * @throws RuntimeException (or subclass of) on error, typically UncheckedIOException.
     */
    private FileInfo getFileInfoForExisting() {
        Optional<Instant> lastModifiedTime = getLastModifiedTimeIfExists();
        if (!lastModifiedTime.isPresent()) {
            return FileInfo.forMissingFile(path);
        }

        Optional<byte[]> content = readContentIfExists();
        if (!content.isPresent()) {
            return FileInfo.forMissingFile(path);
        }

        return FileInfo.forExistingFile(path, lastModifiedTime.get(), content.get());
    }

    private Optional<Instant> getLastModifiedTimeIfExists() {
        try {
            return Optional.of(filesApi.getLastModifiedTime(path));
        } catch (NoSuchFileException e) {
            // File no longer exists.
            return Optional.empty();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Optional<byte[]> readContentIfExists() {
        try {
            return Optional.of(filesApi.readAllBytes(path));
        } catch (NoSuchFileException e) {
            // File no longer exists.
            return Optional.empty();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Optional<FileInfo> createForMissingFile(FileInfo lastFileInfo) {
        if (lastFileInfo.exists()) {
            return Optional.of(FileInfo.forMissingFile(path));
        } else {
            return Optional.empty();
        }
    }
}
