// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.hosted.node.admin.task.util.file;

import java.nio.file.attribute.PosixFileAttributes;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Instant;

/**
 * This wrapper around PosixFileAttributes.
 * 
 * @author hakonhall
 */
public class FileAttributes {
    private final PosixFileAttributes attributes;

    FileAttributes(PosixFileAttributes attributes) {
        this.attributes = attributes;
    }

    public Instant lastModifiedTime() { return attributes.lastModifiedTime().toInstant(); }
    public String owner() { return attributes.owner().getName(); }
    public String group() { return attributes.group().getName(); }
    public String permissions() { return PosixFilePermissions.toString(attributes.permissions()); }
    public boolean isRegularFile() { return attributes.isRegularFile(); }
    public boolean isDirectory() { return attributes.isDirectory(); }
}
