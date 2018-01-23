// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.io;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.GroupPrincipal;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.file.attribute.UserPrincipal;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.util.Set;

/**
 * File system operations to be mocked in unit tests.
 */
public class FileSystem {
    public FileSystemPath withPath(Path path) {
        return new FileSystemPath(this, path);
    }

    public boolean isDirectory(Path path) {
        return path.toFile().isDirectory();
    }

    public boolean isRegularFile(Path path) {
        return path.toFile().isFile();
    }

    public void createDirectory(Path path, FileAttribute<?>... attributes) {
        uncheck(() -> Files.createDirectory(path, attributes));
    }

    public String readUtf8File(Path path) {
        byte[] byteContent = uncheck(() -> Files.readAllBytes(path));
        return new String(byteContent, StandardCharsets.UTF_8);
    }

    public void writeUtf8File(Path path, String content, OpenOption... options) {
        byte[] contentInUtf8 = content.getBytes(StandardCharsets.UTF_8);
        uncheck(() -> Files.write(path, contentInUtf8, options));
    }

    private PosixFileAttributes getAttributes(Path path) {
        return uncheck(() ->
                Files.getFileAttributeView(path, PosixFileAttributeView.class).readAttributes());
    }

    public String getPermissions(Path path) {
        return PosixFilePermissions.toString(getAttributes(path).permissions());
    }

    /**
     * @param permissions Example: "rwxr-x---" means rwx for owner, rx for group,
     *                    and no permissions for others.
     */
    public void setPermissions(Path path, String permissions) {
        Set<PosixFilePermission> permissionSet;
        try {
            permissionSet = PosixFilePermissions.fromString(permissions);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Failed to set permissions '" +
                    permissions + "' on path " + path, e);
        }

        uncheck(() -> Files.setPosixFilePermissions(path, permissionSet));
    }

    public String getOwner(Path path) {
        return getAttributes(path).owner().getName();
    }

    public void setOwner(Path path, String owner) {
        UserPrincipalLookupService service = path.getFileSystem().getUserPrincipalLookupService();
        UserPrincipal principal = uncheck(() -> service.lookupPrincipalByName(owner));
        uncheck(() -> Files.setOwner(path, principal));
    }

    public String getGroup(Path path) {
        return getAttributes(path).group().getName();
    }

    public void setGroup(Path path, String group) {
        UserPrincipalLookupService service = path.getFileSystem().getUserPrincipalLookupService();
        GroupPrincipal principal = uncheck(() -> service.lookupPrincipalByGroupName(group));
        uncheck(() -> Files.getFileAttributeView(path, PosixFileAttributeView.class).setGroup(principal));
    }

    @FunctionalInterface
    private interface SupplierThrowingIOException<T> {
        T get() throws IOException;
    }

    private static <T> T uncheck(SupplierThrowingIOException<T> supplier) {
        try {
            return supplier.get();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @FunctionalInterface
    private interface RunnableThrowingIOException<T> {
        void run() throws IOException;
    }

    private static <T> void uncheck(RunnableThrowingIOException<T> runnable) {
        try {
            runnable.run();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
