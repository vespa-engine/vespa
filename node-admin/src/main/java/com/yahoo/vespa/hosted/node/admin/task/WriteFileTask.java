// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.task;

import com.yahoo.vespa.hosted.node.admin.io.FileSystemPath;
import org.glassfish.jersey.internal.util.Producer;

import java.nio.file.Path;
import java.util.Optional;

public class WriteFileTask implements Task {
    private final Path path;
    private final Producer<String> contentProducer;

    private Optional<String> owner = Optional.empty();
    private Optional<String> group = Optional.empty();
    private Optional<String> permissions = Optional.empty();

    public WriteFileTask(Path path, Producer<String> contentProducer) {
        this.path = path;
        this.contentProducer = contentProducer;
    }

    public WriteFileTask withOwner(String owner) {
        this.owner = Optional.of(owner);
        return this;
    }

    public WriteFileTask withGroup(String group) {
        this.group = Optional.of(group);
        return this;
    }

    /**
     * @param permissions of the form "rwxr-x---".
     */
    public WriteFileTask withPermissions(String permissions) {
        this.permissions = Optional.of(permissions);
        return this;
    }

    @Override
    public boolean execute(TaskContext context) {
        final FileSystemPath fileSystemPath = context.getFileSystem().withPath(path);

        // TODO: Only return false if content, permission, etc would be unchanged.
        if (fileSystemPath.isRegularFile()) {
            return false;
        }

        context.executeSubtask(new MakeDirectoryTask(path.getParent()).withParents());

        String content = contentProducer.call();
        fileSystemPath.writeUtf8File(content);
        permissions.ifPresent(fileSystemPath::setPermissions);
        owner.ifPresent(fileSystemPath::setOwner);
        group.ifPresent(fileSystemPath::setGroup);

        return true;
    }

    public Path getPath() {
        return path;
    }

    public Producer<String> getContentProducer() {
        return contentProducer;
    }

    public Optional<String> getOwner() {
        return owner;
    }

    public Optional<String> getGroup() {
        return group;
    }

    public Optional<String> getPermissions() {
        return permissions;
    }
}
