// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.task.util.fs;

import com.yahoo.vespa.hosted.node.admin.nodeagent.UserScope;

import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.WatchService;
import java.util.Set;

/**
 * @author freva
 */
public class ContainerFileSystem extends FileSystem {

    private final ContainerFileSystemProvider containerFsProvider;
    private final Path containerRootOnHost;

    ContainerFileSystem(ContainerFileSystemProvider containerFsProvider, Path containerRootOnHost) {
        this.containerFsProvider = containerFsProvider;
        this.containerRootOnHost = containerRootOnHost;
    }

    public Path containerRootOnHost() {
        return containerRootOnHost;
    }

    public void createRoot() {
        provider().createFileSystemRoot();
    }

    @Override
    public ContainerFileSystemProvider provider() {
        return containerFsProvider;
    }

    @Override
    public boolean isOpen() {
        return true;
    }

    @Override
    public boolean isReadOnly() {
        return false;
    }

    @Override
    public String getSeparator() {
        return "/";
    }

    @Override
    public Set<String> supportedFileAttributeViews() {
        return Set.of("basic", "posix", "unix", "owner");
    }

    @Override
    public ContainerUserPrincipalLookupService getUserPrincipalLookupService() {
        return containerFsProvider.userPrincipalLookupService();
    }

    @Override
    public ContainerPath getPath(String first, String... more) {
        return ContainerPath.fromPathInContainer(this, Path.of(first, more), getUserPrincipalLookupService().userScope().root());
    }

    @Override
    public void close() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Iterable<Path> getRootDirectories() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Iterable<FileStore> getFileStores() {
        throw new UnsupportedOperationException();
    }

    @Override
    public PathMatcher getPathMatcher(String syntaxAndPattern) {
        throw new UnsupportedOperationException();
    }

    @Override
    public WatchService newWatchService() {
        throw new UnsupportedOperationException();
    }

    public static ContainerFileSystem create(Path containerStorageRoot, UserScope userScope) {
        return new ContainerFileSystemProvider(containerStorageRoot, userScope).getFileSystem(null);
    }
}
