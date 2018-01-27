// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.task.util.file;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.GroupPrincipal;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.file.attribute.UserPrincipal;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import static com.yahoo.vespa.hosted.node.admin.task.util.file.IOExceptionUtil.uncheck;

// @Immutable
public class UnixPath {
    private final Path path;

    public UnixPath(Path path) {
        this.path = path;
    }

    public UnixPath(String path) {
        this(Paths.get(path));
    }

    public Path toPath() {
        return path;
    }

    public boolean createParents() {
        Path parent = path.getParent();
        if (Files.isDirectory(parent)) {
            return false;
        }

        uncheck(() -> Files.createDirectories(parent));
        return true;
    }

    public String readUtf8File() {
        byte[] byteContent = uncheck(() -> Files.readAllBytes(path));
        return new String(byteContent, StandardCharsets.UTF_8);
    }

    public void writeUtf8File(String content, OpenOption... options) {
        byte[] contentInUtf8 = content.getBytes(StandardCharsets.UTF_8);
        uncheck(() -> Files.write(path, contentInUtf8, options));
    }

    public String getPermissions() {
        return getAttributes().permissions();
    }

    /**
     * @param permissions Example: "rwxr-x---" means rwx for owner, rx for group,
     *                    and no permissions for others.
     */
    public void setPermissions(String permissions) {
        Set<PosixFilePermission> permissionSet;
        try {
            permissionSet = PosixFilePermissions.fromString(permissions);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Failed to set permissions '" +
                    permissions + "' on path " + path, e);
        }

        uncheck(() -> Files.setPosixFilePermissions(path, permissionSet));
    }

    public String getOwner() {
        return getAttributes().owner();
    }

    public void setOwner(String owner) {
        UserPrincipalLookupService service = path.getFileSystem().getUserPrincipalLookupService();
        UserPrincipal principal = uncheck(() -> service.lookupPrincipalByName(owner));
        uncheck(() -> Files.setOwner(path, principal));
    }

    public String getGroup() {
        return getAttributes().group();
    }

    public void setGroup(String group) {
        UserPrincipalLookupService service = path.getFileSystem().getUserPrincipalLookupService();
        GroupPrincipal principal = uncheck(() -> service.lookupPrincipalByGroupName(group));
        uncheck(() -> Files.getFileAttributeView(path, PosixFileAttributeView.class).setGroup(principal));
    }

    public Instant getLastModifiedTime() {
        return getAttributes().lastModifiedTime();
    }

    public FileAttributes getAttributes() {
        PosixFileAttributes attributes = uncheck(() ->
                Files.getFileAttributeView(path, PosixFileAttributeView.class).readAttributes());
        return new FileAttributes(attributes);
    }

    public Optional<FileAttributes> getAttributesIfExists() {
        return IOExceptionUtil.ifExists(() -> getAttributes());
    }

    @Override
    public String toString() {
        return path.toString();
    }
}
