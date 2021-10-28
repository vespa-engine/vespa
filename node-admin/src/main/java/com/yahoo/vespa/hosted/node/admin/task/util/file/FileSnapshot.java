// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.task.util.file;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;

/**
 * A snapshot of the attributes of the file for a given path, and file content if it is a regular file.
 *
 * @author hakonhall
 */
public class FileSnapshot {
    private final Path path;
    private final Optional<FileAttributes> attributes;
    private final Optional<byte[]> content;

    public static FileSnapshot forPath(Path path) { return forNonExistingFile(path).snapshot(); }

    /** Guaranteed to not throw any exceptions. */
    public static FileSnapshot forNonExistingFile(Path path) {
        return new FileSnapshot(path, Optional.empty(), Optional.empty());
    }

    private static FileSnapshot forRegularFile(Path path, FileAttributes attributes, byte[] content) {
        if (!attributes.isRegularFile()) throw new IllegalArgumentException(path + " is not a regular file");
        return new FileSnapshot(path, Optional.of(attributes), Optional.of(content));
    }

    private static FileSnapshot forOtherFile(Path path, FileAttributes attributes) {
        if (attributes.isRegularFile()) throw new IllegalArgumentException(path + " is a regular file");
        return new FileSnapshot(path, Optional.of(attributes), Optional.empty());
    }

    private FileSnapshot(Path path, Optional<FileAttributes> attributes, Optional<byte[]> content) {
        this.path = path;
        this.attributes = attributes;
        this.content = content;
    }

    public Path path() { return path; }

    /** Whether there was a file (or directory) at path. */
    public boolean exists() { return attributes.isPresent(); }

    /** Returns the file attributes if the file exists. */
    public Optional<FileAttributes> attributes() { return attributes; }

    /** Returns the file content if the file exists and is a regular file. */
    public Optional<byte[]> content() { return content; }

    /** Returns the file UTF-8 content if it exists and is a regular file. */
    public Optional<String> utf8Content() { return content.map(c -> new String(c, StandardCharsets.UTF_8)); }

    /** Returns an up-to-date snapshot of the path, possibly {@code this} if last modified time has not changed. */
    public FileSnapshot snapshot() {
        Optional<FileAttributes> currentAttributes = new UnixPath(path).getAttributesIfExists();
        if (currentAttributes.isPresent()) {

            // 'this' may still be valid, depending on last modified times.
            if (attributes.isPresent()) {
                Instant previousModifiedTime = attributes.get().lastModifiedTime();
                Instant currentModifiedTime = currentAttributes.get().lastModifiedTime();
                if (currentModifiedTime.compareTo(previousModifiedTime) <= 0) {
                    return this;
                }
            }

            if (currentAttributes.get().isRegularFile()) {
                Optional<byte[]> content = IOExceptionUtil.ifExists(() -> Files.readAllBytes(path));
                return content.map(bytes -> FileSnapshot.forRegularFile(path, currentAttributes.get(), bytes))
                        // File was removed after getting attributes and before getting content.
                        .orElseGet(() -> FileSnapshot.forNonExistingFile(path));
            } else {
                return FileSnapshot.forOtherFile(path, currentAttributes.get());
            }
        } else {
            return attributes.isPresent() ? FileSnapshot.forNonExistingFile(path) : this /* avoid allocation */;
        }
    }
}
