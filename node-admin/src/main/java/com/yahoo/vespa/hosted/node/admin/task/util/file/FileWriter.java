// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.task.util.file;

import com.yahoo.vespa.hosted.node.admin.component.TaskContext;
import org.glassfish.jersey.internal.util.Producer;

import java.nio.file.Files;
import java.nio.file.Path;

public class FileWriter {
    private final Path path;
    private final FileSync fileSync;
    private final PartialFileData.Builder fileDataBuilder = PartialFileData.builder();
    private final Producer<String> contentProducer;

    private boolean overwriteExistingFile = true;

    public FileWriter(Path path, Producer<String> contentProducer) {
        this.path = path;
        this.fileSync = new FileSync(path);
        this.contentProducer = contentProducer;
    }

    public FileWriter withOwner(String owner) {
        fileDataBuilder.withOwner(owner);
        return this;
    }

    public FileWriter withGroup(String group) {
        fileDataBuilder.withGroup(group);
        return this;
    }

    public FileWriter withPermissions(String permissions) {
        fileDataBuilder.withPermissions(permissions);
        return this;
    }

    public FileWriter onlyIfFileDoesNotAlreadyExist() {
        overwriteExistingFile = false;
        return this;
    }

    public boolean converge(TaskContext context) {
        if (!overwriteExistingFile && Files.isRegularFile(path)) {
            return false;
        }

        fileDataBuilder.withContent(contentProducer.call());
        PartialFileData fileData = fileDataBuilder.create();
        return fileSync.convergeTo(context, fileData);
    }
}
