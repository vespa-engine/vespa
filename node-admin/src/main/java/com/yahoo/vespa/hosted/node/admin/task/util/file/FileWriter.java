// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.task.util.file;

import com.yahoo.vespa.hosted.node.admin.component.TaskContext;
import org.glassfish.jersey.internal.util.Producer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.logging.Logger;

public class FileWriter {
    private static final Logger logger = Logger.getLogger(FileWriter.class.getName());

    private final Path path;
    private final Producer<String> contentProducer;

    private Optional<String> owner = Optional.empty();
    private Optional<String> group = Optional.empty();
    private Optional<String> permissions = Optional.empty();

    public FileWriter(Path path, Producer<String> contentProducer) {
        this.path = path;
        this.contentProducer = contentProducer;
    }

    public FileWriter withOwner(String owner) {
        this.owner = Optional.of(owner);
        return this;
    }

    public FileWriter withGroup(String group) {
        this.group = Optional.of(group);
        return this;
    }

    public FileWriter withPermissions(String permissions) {
        this.permissions = Optional.of(permissions);
        return this;
    }

    public boolean converge(TaskContext context) {
        // TODO: Only return false if content, permission, etc would be unchanged.
        if (Files.isRegularFile(path)) {
            return false;
        }

        context.logSystemModification(logger,"Writing file " + path);

        String content = contentProducer.call();

        UnixPath unixPath = new UnixPath(path);
        unixPath.createParents();
        unixPath.writeUtf8File(content);
        permissions.ifPresent(unixPath::setPermissions);
        owner.ifPresent(unixPath::setOwner);
        group.ifPresent(unixPath::setGroup);

        return true;
    }
}
