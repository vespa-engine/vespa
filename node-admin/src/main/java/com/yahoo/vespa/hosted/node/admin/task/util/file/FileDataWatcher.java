// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.hosted.node.admin.task.util.file;

import javax.annotation.concurrent.ThreadSafe;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * A FileDataWatcher can be used to test for the existence of a regular file at a fixed path,
 * and if so, get the file attributes and contents.
 *
 * @author hakonhall
 */
@ThreadSafe
public class FileDataWatcher {
    private static final Logger logger = Logger.getLogger(FileDataWatcher.class.getName());

    private final UnixPath path;

    private volatile FileData fileData = null;

    /**
     * @param path The path to watch.
     */
    public FileDataWatcher(Path path) {
        this.path = new UnixPath(path);
    }

    public Path getPath() {
        return path.toPath();
    }

    public Optional<FileData> getIfContentDifferent(FileData oldFileData) {
        return makeFileDataIfDifferent(oldFileData);
    }

    public FileData getFileData() {
        // This function accesses volatile fileData once, and sets it at most once.

        FileData snapshot = fileData;
        if (snapshot == null) {
            FileData newValue = readFileData(path);
            fileData = newValue;
            return newValue;
        }

        Optional<FileData> newValue = makeFileDataIfDifferent(snapshot);
        if (newValue.isPresent()) {
            fileData = newValue.get();
            return newValue.get();
        } else {
            return snapshot;
        }
    }

    /** Returns empty if FileData hasn't changed. Otherwise, return an up-to-date value. */
    static Optional<FileData> makeFileDataIfDifferent(FileData oldFileData) {
        // Optimization: If fileData doesn't exist, then we expect the file to also no exist.
        if (!oldFileData.exists() && Files.notExists(oldFileData.path())) {
            return Optional.empty();
        }

        UnixPath path = oldFileData.unixPath();
        Optional<FileAttributes> attributes = path.getAttributesIfExists();

        if (attributes.isPresent()) {
            if (oldFileData.exists()) {
                if (attributes.get().lastModifiedTime().isAfter(oldFileData.attributes().lastModifiedTime())) {
                    // Content may have changed since last time.
                    return Optional.of(readFileContent(path, attributes.get()));
                } else if (!attributes.get().equals(oldFileData.attributes())) {
                    // Even though the file contents haven't changed, the file attributes have
                    // so we need to make a new FileData.
                    return Optional.of(updateFileAttributes(oldFileData, attributes.get()));
                }
            } else {
                // File has been created (or it's the first call to getFileData)
                return Optional.of(readFileContent(path, attributes.get()));
            }
        } else if (oldFileData.exists()) {
            return Optional.of(FileData.forMissingFile(path));
        }

        return Optional.empty();
    }

    private static FileData readFileData(UnixPath path) {
        return path.getAttributesIfExists()
                .map(attributes -> FileData.forFile(path, attributes, path.readUtf8File()))
                .orElseGet(() -> FileData.forMissingFile(path));
    }

    private static FileData readFileContent(UnixPath path, FileAttributes newAttributes) {
        return FileData.forFile(path, newAttributes, path.readUtf8File());
    }

    private static FileData updateFileAttributes(FileData fileData, FileAttributes newAttributes) {
        return FileData.forFile(fileData.unixPath(), newAttributes, fileData.utf8Content());
    }
}
