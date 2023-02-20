// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.task.util.file;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.GroupPrincipal;
import java.nio.file.attribute.PosixFileAttributeView;
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

    private static final Set<OpenOption> DEFAULT_OPEN_OPTIONS =
            Set.of(StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);

    private final Path path;

    public UnixPath(Path path) { this.path = path; }
    public UnixPath(String path) { this(Path.of(path)); }

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
            throw new IllegalStateException("Path has no filename: '" + path + "'");
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

    public UnixPath writeUtf8File(String content, String permissions, OpenOption... options) {
        return writeBytes(content.getBytes(StandardCharsets.UTF_8), permissions, options);
    }

    public UnixPath writeBytes(byte[] content, OpenOption... options) {
        return writeBytes(content, null, options);
    }

    public UnixPath writeBytes(byte[] content, String permissions, OpenOption... options) {
        FileAttribute<?>[] attributes = Optional.ofNullable(permissions)
                .map(this::permissionsAsFileAttributes)
                .orElseGet(() -> new FileAttribute<?>[0]);

        Set<OpenOption> optionsSet = options.length == 0 ? DEFAULT_OPEN_OPTIONS : Set.of(options);

        try (SeekableByteChannel channel = Files.newByteChannel(path, optionsSet, attributes)) {
            channel.write(ByteBuffer.wrap(content));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return this;
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

    public int getOwnerId() {
        return getAttributes().ownerId();
    }

    public UnixPath setOwner(String user) { return setOwner(user, "user"); }
    public UnixPath setOwnerId(int uid) { return setOwner(String.valueOf(uid), "uid"); }
    private UnixPath setOwner(String owner, String type) {
        UserPrincipalLookupService service = path.getFileSystem().getUserPrincipalLookupService();
        UserPrincipal principal = uncheck(
                () -> service.lookupPrincipalByName(owner),
                "While looking up %s %s", type, owner);
        uncheck(() -> Files.setOwner(path, principal));
        return this;
    }

    public int getGroupId() {
        return getAttributes().groupId();
    }

    public UnixPath setGroup(String group) { return setGroup(group, "group"); }
    public UnixPath setGroupId(int gid) { return setGroup(String.valueOf(gid), "gid"); }
    private UnixPath setGroup(String group, String type) {
        UserPrincipalLookupService service = path.getFileSystem().getUserPrincipalLookupService();
        GroupPrincipal principal = uncheck(
                () -> service.lookupPrincipalByGroupName(group),
                "While looking up group %s %s", type, group);
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
        return uncheck(() -> FileAttributes.fromAttributes(Files.readAttributes(path, "unix:*")));
    }

    public Optional<FileAttributes> getAttributesIfExists() {
        return ifExists(this::getAttributes);
    }

    public UnixPath createNewFile(String... permissions) {
        uncheck(() -> Files.createFile(path, permissionsAsFileAttributes(permissions)));
        return this;
    }

    public UnixPath createParents(String... permissions) {
        getParent().createDirectories(permissions);
        return this;
    }

    /** Create directory with given permissions, unless it already exists, and return this. */
    public UnixPath createDirectory(String... permissions) {
        try {
            Files.createDirectory(path, permissionsAsFileAttributes(permissions));
        } catch (FileAlreadyExistsException ignore) {
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return this;
    }

    public UnixPath createDirectories(String... permissions) {
        uncheck(() -> Files.createDirectories(path, permissionsAsFileAttributes(permissions)));
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

    public boolean deleteIfExists() {
        return uncheck(() -> Files.deleteIfExists(path));
    }

    /** @return false path does not exist, is not a directory, or has at least one entry. */
    public boolean isEmptyDirectory() {
        try (var entryStream = Files.list(path)) {
            return entryStream.findAny().isEmpty();
        } catch (NotDirectoryException | NoSuchFileException e) {
            return false;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
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
            throw new UncheckedIOException("Failed to list contents of directory " + path.toAbsolutePath(), e);
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

    @Override
    public String toString() {
        return path.toString();
    }

    private FileAttribute<?>[] permissionsAsFileAttributes(String... permissions) {
        if (permissions.length == 0) return new FileAttribute<?>[0];
        if (permissions.length > 1)
            throw new IllegalArgumentException("Expected permissions to not be set or be a single string");

       return new FileAttribute<?>[]{PosixFilePermissions.asFileAttribute(getPosixFilePermissionsFromString(permissions[0]))};
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
