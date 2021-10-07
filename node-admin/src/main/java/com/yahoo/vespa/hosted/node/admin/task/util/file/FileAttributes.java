// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.task.util.file;

import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Instant;
import java.util.Map;
import java.util.Set;

/**
 * This wrapper around PosixFileAttributes.
 *
 * @author hakonhall
 */
public class FileAttributes {

    private final Instant lastModifiedTime;
    private final int ownerId;
    private final int groupId;
    private final String permissions;
    private final boolean isRegularFile;
    private final boolean isDirectory;
    private final long size;

    public FileAttributes(Instant lastModifiedTime, int ownerId, int groupId, String permissions, boolean isRegularFile, boolean isDirectory, long size) {
        this.lastModifiedTime = lastModifiedTime;
        this.ownerId = ownerId;
        this.groupId = groupId;
        this.permissions = permissions;
        this.isRegularFile = isRegularFile;
        this.isDirectory = isDirectory;
        this.size = size;
    }

    public Instant lastModifiedTime() { return lastModifiedTime; }
    public int ownerId() { return ownerId; }
    public int groupId() { return groupId; }
    public String permissions() { return permissions; }
    public boolean isRegularFile() { return isRegularFile; }
    public boolean isDirectory() { return isDirectory; }
    public long size() { return size; }

    @SuppressWarnings("unchecked")
    static FileAttributes fromAttributes(Map<String, Object> attributes) {
        return new FileAttributes(
                ((FileTime) attributes.get("lastModifiedTime")).toInstant(),
                (int) attributes.get("uid"),
                (int) attributes.get("gid"),
                PosixFilePermissions.toString(((Set<PosixFilePermission>) attributes.get("permissions"))),
                (boolean) attributes.get("isRegularFile"),
                (boolean) attributes.get("isDirectory"),
                (long) attributes.get("size"));
    }
}
