// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.task.util.file;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileTime;
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
import java.util.stream.Stream;

import static com.yahoo.vespa.hosted.node.admin.task.util.file.IOExceptionUtil.ifExists;
import static com.yahoo.yolean.Exceptions.uncheck;

/**
 * Thin wrapper around java.nio.file.Path, especially nice for UNIX-specific features.
 *
 * @author hakonhall
 */
// @Immutable
public class UnixPath {
    private final Path path;

    public UnixPath(Path path) { this.path = path; }
    public UnixPath(String path) { this(Paths.get(path)); }

    public Path toPath() { return path; }
    public UnixPath resolve(String relativeOrAbsolutePath) { return new UnixPath(path.resolve(relativeOrAbsolutePath)); }

    public UnixPath getParent() {
        Path parentPath = path.getParent();
        if (parentPath == null) {
            throw new IllegalStateException("Path has no parent directory: '" + path + "'");
        }

        return new UnixPath(parentPath);
    }

    public String getFilename() {
        Path filename = path.getFileName();
        if (filename == null) {
            // E.g. "/".
            throw new IllegalStateException("Path has no filename: '" + path.toString() + "'");
        }

        return filename.toString();
    }

    public boolean exists() { return Files.exists(path); }

    public String readUtf8File() {
        return new String(readBytes(), StandardCharsets.UTF_8);
    }

    public Optional<String> readUtf8FileIfExists() {
        try {
            return Optional.of(Files.readString(path));
        } catch (NoSuchFileException ignored) {
            return Optional.empty();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public byte[] readBytes() {
        return uncheck(() -> Files.readAllBytes(path));
    }

    /** Reads and returns all bytes contained in this path, if any such path exists. */
    public Optional<byte[]> readBytesIfExists() {
        try {
            return Optional.of(Files.readAllBytes(path));
        } catch (NoSuchFileException ignored) {
            return Optional.empty();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public UnixPath writeUtf8File(String content, OpenOption... options) {
        return writeBytes(content.getBytes(StandardCharsets.UTF_8), options);
    }

    public UnixPath writeBytes(byte[] content, OpenOption... options) {
        uncheck(() -> Files.write(path, content, options));
        return this;
    }

    public UnixPath atomicWriteUt8(String content) {
        return atomicWriteBytes(content.getBytes(StandardCharsets.UTF_8));
    }

    /** Write a file to the same dir as this, and then atomically move it to this' path. */
    public UnixPath atomicWriteBytes(byte[] content) {
        UnixPath temporaryPath = getParent().resolve(getFilename() + ".10Ia2f4N5");
        temporaryPath.writeBytes(content);
        temporaryPath.atomicMove(path);
        return this;
    }

    public String getPermissions() {
        return getAttributes().permissions();
    }

    /**
     * @param permissions Example: "rwxr-x---" means rwx for owner, rx for group,
     *                    and no permissions for others.
     */
    public UnixPath setPermissions(String permissions) {
        Set<PosixFilePermission> permissionSet = getPosixFilePermissionsFromString(permissions);
        uncheck(() -> Files.setPosixFilePermissions(path, permissionSet));
        return this;
    }

    public String getOwner() {
        return getAttributes().owner();
    }

    public UnixPath setOwner(String owner) {
        UserPrincipalLookupService service = path.getFileSystem().getUserPrincipalLookupService();
        UserPrincipal principal = uncheck(
                () -> service.lookupPrincipalByName(owner),
                "While looking up user %s", owner);
        uncheck(() -> Files.setOwner(path, principal));
        return this;
    }

    public String getGroup() {
        return getAttributes().group();
    }

    public UnixPath setGroup(String group) {
        UserPrincipalLookupService service = path.getFileSystem().getUserPrincipalLookupService();
        GroupPrincipal principal = uncheck(
                () -> service.lookupPrincipalByGroupName(group),
                "while looking up group %s", group);
        uncheck(() -> Files.getFileAttributeView(path, PosixFileAttributeView.class).setGroup(principal));
        return this;
    }

    public Instant getLastModifiedTime() {
        return getAttributes().lastModifiedTime();
    }

    public UnixPath updateLastModifiedTime() {
        return setLastModifiedTime(Instant.now());
    }

    public UnixPath setLastModifiedTime(Instant instant) {
        uncheck(() -> Files.setLastModifiedTime(path, FileTime.from(instant)));
        return this;
    }

    public FileAttributes getAttributes() {
        PosixFileAttributes attributes = uncheck(() ->
                Files.getFileAttributeView(path, PosixFileAttributeView.class).readAttributes());
        return new FileAttributes(attributes);
    }

    public Optional<FileAttributes> getAttributesIfExists() {
        return ifExists(this::getAttributes);
    }

    public UnixPath createNewFile() {
        uncheck(() -> Files.createFile(path));
        return this;
    }

    public UnixPath createNewFile(String permissions) {
        FileAttribute<?> attribute = PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString(permissions));
        uncheck(() -> Files.createFile(path, attribute));
        return this;
    }

    public UnixPath createParents() {
        Path parent = path.getParent();
        if (!Files.isDirectory(parent)) {
            uncheck(() -> Files.createDirectories(parent));
        }

        return this;
    }

    public UnixPath createDirectory(String permissions) {
        Set<PosixFilePermission> set = getPosixFilePermissionsFromString(permissions);
        FileAttribute<Set<PosixFilePermission>> attribute = PosixFilePermissions.asFileAttribute(set);
        uncheck(() -> Files.createDirectory(path, attribute));
        return this;
    }

    public UnixPath createDirectory() {
        uncheck(() -> Files.createDirectory(path));
        return this;
    }

    public UnixPath createDirectories() {
        uncheck(() -> Files.createDirectories(path));
        return this;
    }

    /**
     * Returns whether this path is a directory. Symlinks are followed, so this returns true for symlinks pointing to a
     * directory.
     */
    public boolean isDirectory() {
        return uncheck(() -> Files.isDirectory(path));
    }

    /** Returns whether this is a symlink */
    public boolean isSymbolicLink() {
        return Files.isSymbolicLink(path);
    }

    /**
     * Similar to rm -rf file:
     * - It's not an error if file doesn't exist
     * - If file is a directory, it and all content is removed
     * - For symlinks: Only the symlink is removed, not what the symlink points to
     */
    public boolean deleteRecursively() {
        if (!isSymbolicLink() && isDirectory()) {
            try (Stream<UnixPath> paths = listContentsOfDirectory()) {
                paths.forEach(UnixPath::deleteRecursively);
            }
        }
        return uncheck(() -> Files.deleteIfExists(path));
    }

    public UnixPath deleteIfExists() {
        uncheck(() -> Files.deleteIfExists(path));
        return this;
    }

    /** Lists the contents of this as a stream. Callers should use try-with to ensure that the stream is closed */
    public Stream<UnixPath> listContentsOfDirectory() {
        try {
            // Avoid the temptation to collect the stream here as collecting a directory with a high number of entries
            // can quickly lead to out of memory conditions
            return Files.list(path).map(UnixPath::new);
        } catch (NoSuchFileException ignored) {
            return Stream.empty();
        } catch (IOException e) {
            throw new RuntimeException("Failed to list contents of directory " + path.toAbsolutePath(), e);
        }
    }

    /** This path must be on the same file system as the to-path. Returns UnixPath of 'to'. */
    public UnixPath atomicMove(Path to) {
        uncheck(() -> Files.move(path, to, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING));
        return new UnixPath(to);
    }

    public boolean moveIfExists(Path to) {
        try {
            Files.move(path, to);
            return true;
        } catch (NoSuchFileException ignored) {
            return false;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Creates a symbolic link from {@code link} to {@code this} (the target)
     * @param link the path for the symbolic link
     * @return the path to the symbolic link
     */
    public UnixPath createSymbolicLink(Path link) {
        uncheck(() -> Files.createSymbolicLink(link, path));
        return new UnixPath(link);
    }

    public FileSnapshot getFileSnapshot() { return FileSnapshot.forPath(path); }

    @Override
    public String toString() {
        return path.toString();
    }

    private Set<PosixFilePermission> getPosixFilePermissionsFromString(String permissions) {
        try {
            return PosixFilePermissions.fromString(permissions);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Failed to set permissions '" +
                    permissions + "' on path " + path, e);
        }
    }
}
