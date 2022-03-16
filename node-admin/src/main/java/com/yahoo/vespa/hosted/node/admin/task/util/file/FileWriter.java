// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.task.util.file;

import com.yahoo.vespa.hosted.node.admin.component.TaskContext;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Write a file
 *
 * @author hakonhall
 */
public class FileWriter {
    private final Path path;
    private final FileSync fileSync;
    private final PartialFileData.Builder fileDataBuilder = PartialFileData.builder();
    private final Optional<ByteArraySupplier> contentProducer;

    private boolean atomicWrite = false;
    private boolean overwriteExistingFile = true;

    public FileWriter(Path path) {
        this(path, Optional.empty());
    }

    public FileWriter(Path path, Supplier<String> contentProducer) {
        this(path, () -> contentProducer.get().getBytes(StandardCharsets.UTF_8));
    }

    public FileWriter(Path path, ByteArraySupplier contentProducer) {
        this(path, Optional.of(contentProducer));
    }

    private FileWriter(Path path, Optional<ByteArraySupplier> contentProducer) {
        this.path = path;
        this.fileSync = new FileSync(path);
        this.contentProducer = contentProducer;
    }

    public Path path() { return path; }

    public FileWriter withOwnerId(int ownerId) {
        fileDataBuilder.withOwnerId(ownerId);
        return this;
    }

    public FileWriter withGroupId(int groupId) {
        fileDataBuilder.withGroupId(groupId);
        return this;
    }

    /** @see UnixPath#setPermissions */
    public FileWriter withPermissions(String permissions) {
        fileDataBuilder.withPermissions(permissions);
        return this;
    }

    public FileWriter atomicWrite(boolean atomicWrite) {
        this.atomicWrite = atomicWrite;
        return this;
    }

    public FileWriter onlyIfFileDoesNotAlreadyExist() {
        overwriteExistingFile = false;
        return this;
    }

    public boolean converge(TaskContext context) {
        return converge(context, contentProducer.orElseThrow().get());
    }

    public boolean converge(TaskContext context, String utf8Content) {
        return converge(context, utf8Content.getBytes(StandardCharsets.UTF_8));
    }

    public boolean converge(TaskContext context, byte[] content) {
        if (!overwriteExistingFile && Files.isRegularFile(path)) {
            return false;
        }

        fileDataBuilder.withContent(content);
        PartialFileData fileData = fileDataBuilder.create();
        return fileSync.convergeTo(context, fileData, atomicWrite);
    }

    @FunctionalInterface
    public interface ByteArraySupplier extends Supplier<byte[]> { }
}
