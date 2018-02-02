// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.task.util.file;

import com.yahoo.vespa.hosted.node.admin.component.TaskContext;

import java.io.UncheckedIOException;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Class to ensure a directory exists with the correct owner, group, and permissions.
 *
 * @author hakonhall
 */
public class MakeDirectory {
    private static final Logger logger = Logger.getLogger(MakeDirectory.class.getName());

    private final UnixPath path;
    private final AttributeSync attributeSync;

    private boolean createParents = false;

    public MakeDirectory(Path path) {
        this.path = new UnixPath(path);
        this.attributeSync = new AttributeSync(path);
    }

    /**
     * Warning: The owner, group, and permissions of any created parent directories are NOT modified
     */
    public MakeDirectory createParents() { this.createParents = true; return this; }

    public MakeDirectory withOwner(String owner) { attributeSync.withOwner(owner); return this; }
    public MakeDirectory withGroup(String group) { attributeSync.withGroup(group); return this; }
    public MakeDirectory withPermissions(String permissions) {
        attributeSync.withPermissions(permissions);
        return this;
    }

    public boolean converge(TaskContext context) {
        boolean systemModified = false;

        FileAttributesCache attributes = new FileAttributesCache(path);
        if (attributes.exists()) {
            if (!attributes.get().isDirectory()) {
                throw new UncheckedIOException(new NotDirectoryException(path.toString()));
            }
        } else {
            if (createParents) {
                // We'll skip logginer system modification here, as we'll log about the creation
                // of the directory next.
                path.createParents();
            }

            context.recordSystemModification(logger, "Creating directory " + path);
            systemModified = true;

            Optional<String> permissions = attributeSync.getPermissions();
            if (permissions.isPresent()) {
                path.createDirectory(permissions.get());
            } else {
                path.createDirectory();
            }
        }

        systemModified |= attributeSync.converge(context, attributes);

        return systemModified;
    }
}
