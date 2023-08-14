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
public record FileAttributes(Instant lastModifiedTime, int ownerId, int groupId, String permissions,
                             boolean isRegularFile, boolean isDirectory, long size, int deviceMajor, int deviceMinor) {

    @SuppressWarnings("unchecked")
    static FileAttributes fromAttributes(Map<String, Object> attributes) {
        long dev_t = (long) attributes.get("dev");

        return new FileAttributes(
                ((FileTime) attributes.get("lastModifiedTime")).toInstant(),
                (int) attributes.get("uid"),
                (int) attributes.get("gid"),
                PosixFilePermissions.toString(((Set<PosixFilePermission>) attributes.get("permissions"))),
                (boolean) attributes.get("isRegularFile"),
                (boolean) attributes.get("isDirectory"),
                (long) attributes.get("size"),
                deviceMajor(dev_t), deviceMinor(dev_t));
    }

    // Encoded as MMMM Mmmm mmmM MMmm, where M is a hex digit of the major number and m is a hex digit of the minor number.
    static int deviceMajor(long dev_t) { return (int) (((dev_t & 0xFFFFF00000000000L) >> 32) | ((dev_t & 0xFFF00) >> 8)); }
    static int deviceMinor(long dev_t) { return (int) (((dev_t & 0x00000FFFFFF00000L) >> 12) |  (dev_t & 0x000FF)); }
}
