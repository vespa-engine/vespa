// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.io;

import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;

/**
 * Convenience class for calling FileSystem methods on a fixed Path.
 */
public class FileSystemPath {
    private final FileSystem fileSystem;
    private final Path path;

    FileSystemPath(FileSystem fileSystem, Path path) {
        this.fileSystem = fileSystem;
        this.path = path;
    }

    public boolean isDirectory() {
        return fileSystem.isDirectory(path);
    }

    public boolean isRegularFile() {
        return fileSystem.isRegularFile(path);
    }

    public FileSystemPath createDirectory(FileAttribute<?>... attributes) {
        fileSystem.createDirectory(path, attributes);
        return this;
    }

    public String readUtf8File() {
        return fileSystem.readUtf8File(path);
    }

    public FileSystemPath writeUtf8File(String content, OpenOption... options) {
        fileSystem.writeUtf8File(path, content, options);
        return this;
    }

    public String getPermissions() {
        return fileSystem.getPermissions(path);
    }

    public FileSystemPath setPermissions(String permissions) {
        fileSystem.setPermissions(path, permissions);
        return this;
    }

    public String getOwner() {
        return fileSystem.getOwner(path);
    }

    public FileSystemPath setOwner(String owner) {
        fileSystem.setOwner(path, owner);
        return this;
    }

    public String getGroup() {
        return fileSystem.getGroup(path);
    }

    public FileSystemPath setGroup(String group) {
        fileSystem.setGroup(path, group);
        return this;
    }
}
