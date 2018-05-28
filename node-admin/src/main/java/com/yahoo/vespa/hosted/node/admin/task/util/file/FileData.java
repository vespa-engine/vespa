// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.hosted.node.admin.task.util.file;

import javax.annotation.concurrent.Immutable;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Represents file attributes and content of a possibly missing regular file.
 *
 * @author hakonhall
 */
@Immutable
public class FileData {
    private final UnixPath path;
    private final Optional<FileAttributes> attributes;
    private final Optional<String> content;

    public static FileData forFile(UnixPath path, FileAttributes attributes, String content) {
        return new FileData(path, Optional.of(attributes), Optional.of(content));
    }

    public static FileData forMissingFile(UnixPath path) {
        return new FileData(path, Optional.empty(), Optional.empty());
    }

    /** The path of the file. */
    public Path path() {
        return path.toPath();
    }

    public UnixPath unixPath() {
        return path;
    }

    /** Whether the file exists. */
    public boolean exists() {
        return attributes.map(FileAttributes::isRegularFile).orElse(false);
    }

    /** Returns file attributes. The file must exist. */
    public FileAttributes attributes() {
        return attributes.get();
    }

    /** The content of the file, assuming UTF-8 encoding. */
    public String utf8Content() {
        return content.get();
    }

    private FileData(UnixPath path, Optional<FileAttributes> attributes, Optional<String> content) {
        this.path = path;
        this.attributes = attributes;
        this.content = content;
    }
}
