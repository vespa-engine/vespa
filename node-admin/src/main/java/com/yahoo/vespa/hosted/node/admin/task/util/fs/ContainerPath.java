// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.task.util.fs;

import com.yahoo.vespa.hosted.node.admin.task.util.file.UnixUser;

import java.io.IOException;
import java.net.URI;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static com.yahoo.vespa.hosted.node.admin.task.util.fs.ContainerFileSystemProvider.toContainerPath;

/**
 * Represents a path in container that is mapped in from the host. ContainerPaths are always normalized and absolute.
 *
 * @author freva
 */
public class ContainerPath implements Path {
    private final ContainerFileSystem containerFs;
    private final Path pathOnHost;
    private final String[] parts;
    private final UnixUser user;

    private ContainerPath(ContainerFileSystem containerFs, Path pathOnHost, String[] parts, UnixUser user) {
        this.containerFs = Objects.requireNonNull(containerFs);
        this.pathOnHost = Objects.requireNonNull(pathOnHost);
        this.parts = Objects.requireNonNull(parts);
        this.user = Objects.requireNonNull(user);

        if (!pathOnHost.isAbsolute())
            throw new IllegalArgumentException("Path host must be absolute: " + pathOnHost);
        Path containerRootOnHost = containerFs.containerRootOnHost();
        if (!pathOnHost.startsWith(containerRootOnHost))
            throw new IllegalArgumentException("Path on host (" + pathOnHost + ") must start with container root on host (" + containerRootOnHost + ")");
    }

    public Path pathOnHost() { return pathOnHost; }
    public String pathInContainer() { return '/' + String.join("/", parts); }
    public ContainerPath withUser(UnixUser user) { return new ContainerPath(containerFs, pathOnHost, parts, user); }
    public UnixUser user() { return user; }

    @Override
    public ContainerFileSystem getFileSystem() {
        return containerFs;
    }

    @Override
    public ContainerPath getRoot() {
        return resolve(containerFs, new String[0], Path.of("/"), user);
    }

    @Override
    public Path getFileName() {
        if (parts.length == 0) return null;
        return Path.of(parts[parts.length - 1]);
    }

    @Override
    public ContainerPath getParent() {
        if (parts.length == 0) return null;
        return new ContainerPath(containerFs, pathOnHost.getParent(), Arrays.copyOf(parts, parts.length-1), user);
    }

    @Override
    public int getNameCount() {
        return parts.length;
    }

    @Override
    public Path getName(int index) {
        return Path.of(parts[index]);
    }

    @Override
    public Path subpath(int beginIndex, int endIndex) {
        if (beginIndex < 0 || beginIndex >= endIndex || endIndex > parts.length)
            throw new IllegalArgumentException();
        if (endIndex - beginIndex == 1) return getName(beginIndex);

        String[] rest = new String[endIndex - beginIndex - 1];
        System.arraycopy(parts, beginIndex + 1, rest, 0, rest.length);
        return Path.of(parts[beginIndex], rest);
    }

    @Override public ContainerPath resolve(Path other) { return resolve(containerFs, parts, other, user); }
    @Override public ContainerPath resolve(String other) { return resolve(Path.of(other)); }
    @Override public ContainerPath resolveSibling(String other) { return resolve(Path.of("..", other)); }

    @Override
    public boolean startsWith(Path other) {
        if (other.getFileSystem() != containerFs) return false;
        String[] otherParts = toContainerPath(other).parts;
        if (parts.length < otherParts.length) return false;

        for (int i = 0; i < otherParts.length; i++) {
            if ( ! parts[i].equals(otherParts[i])) return false;
        }
        return true;
    }

    @Override
    public boolean endsWith(Path other) {
        int offset = parts.length - other.getNameCount();
        // If the other path is longer than this, or the other path is absolute and shorter than this
        if (offset < 0 || (other.isAbsolute() && offset > 0)) return false;

        for (int i = 0; i < other.getNameCount(); i++) {
            if ( ! parts[offset + i].equals(other.getName(i).toString())) return false;
        }
        return true;
    }

    @Override
    public boolean isAbsolute() {
        // All container paths are normalized and absolute
        return true;
    }

    @Override
    public ContainerPath normalize() {
        // All container paths are normalized and absolute
        return this;
    }

    @Override
    public ContainerPath toAbsolutePath() {
        // All container paths are normalized and absolute
        return this;
    }

    @Override
    public ContainerPath toRealPath(LinkOption... options) throws IOException {
        Path realPathOnHost = pathOnHost.toRealPath(options);
        if (realPathOnHost.equals(pathOnHost)) return this;
        return fromPathOnHost(containerFs, realPathOnHost, user);
    }

    @Override
    public Path relativize(Path other) {
        return pathOnHost.relativize(toContainerPath(other).pathOnHost);
    }

    @Override
    public URI toUri() {
        throw new UnsupportedOperationException();
    }

    @Override
    public WatchKey register(WatchService watcher, WatchEvent.Kind<?>[] events, WatchEvent.Modifier... modifiers) throws IOException {
        return pathOnHost.register(watcher, events, modifiers);
    }

    @Override
    public int compareTo(Path other) {
        return pathOnHost.compareTo(toContainerPath(other));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ContainerPath paths = (ContainerPath) o;
        return containerFs.equals(paths.containerFs) && pathOnHost.equals(paths.pathOnHost) && Arrays.equals(parts, paths.parts);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(containerFs, pathOnHost);
        result = 31 * result + Arrays.hashCode(parts);
        return result;
    }

    @Override
    public String toString() {
        return containerFs.containerRootOnHost().getFileName() + ":" + pathInContainer();
    }

    private static ContainerPath resolve(ContainerFileSystem containerFs, String[] currentParts, Path other, UnixUser user) {
        List<String> parts = other.isAbsolute() ? new ArrayList<>() : new ArrayList<>(Arrays.asList(currentParts));
        for (int i = 0; i < other.getNameCount(); i++) {
            String part = other.getName(i).toString();
            if (part.isEmpty() || part.equals(".")) continue;
            if (part.equals("..")) {
                if (!parts.isEmpty()) parts.remove(parts.size() - 1);
                continue;
            }
            parts.add(part);
        }

        return new ContainerPath(containerFs,
                containerFs.containerRootOnHost().resolve(String.join("/", parts)),
                parts.toArray(String[]::new),
                user);
    }

    public static ContainerPath fromPathInContainer(ContainerFileSystem containerFs, Path pathInContainer, UnixUser user) {
        if (!pathInContainer.isAbsolute())
            throw new IllegalArgumentException("Path in container must be absolute: " + pathInContainer);
        return resolve(containerFs, new String[0], pathInContainer, user);
    }

    public static ContainerPath fromPathOnHost(ContainerFileSystem containerFs, Path pathOnHost, UnixUser user) {
        pathOnHost = pathOnHost.normalize();
        Path containerRootOnHost = containerFs.containerRootOnHost();
        Path pathUnderContainerStorage = containerRootOnHost.relativize(pathOnHost);

        if (pathUnderContainerStorage.getNameCount() == 0 || pathUnderContainerStorage.getName(0).toString().isEmpty())
            return new ContainerPath(containerFs, pathOnHost, new String[0], user);
        if (pathUnderContainerStorage.getName(0).toString().equals(".."))
            throw new IllegalArgumentException("Path " + pathOnHost + " is not under container root " + containerRootOnHost);

        List<String> parts = new ArrayList<>();
        for (int i = 0; i < pathUnderContainerStorage.getNameCount(); i++)
            parts.add(pathUnderContainerStorage.getName(i).toString());
        return new ContainerPath(containerFs, pathOnHost, parts.toArray(String[]::new), user);
    }
}
