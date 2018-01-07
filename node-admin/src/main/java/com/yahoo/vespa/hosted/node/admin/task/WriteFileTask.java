// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.task;

import com.yahoo.vespa.hosted.node.admin.io.FileSystemPath;
import org.glassfish.jersey.internal.util.Producer;

import java.nio.file.Path;
import java.util.Optional;

public class WriteFileTask implements Task {
    private final Params params;

    public static class Params {
        private final Path path;
        private final Producer<String> contentProducer;

        private Optional<String> user = Optional.empty();
        private Optional<String> group = Optional.empty();
        private Optional<String> permissions = Optional.empty();

        public Params(Path path, Producer<String> contentProducer) {
            this.path = path;
            this.contentProducer = contentProducer;
        }

        public Params withUser(String user) {
            this.user = Optional.of(user);
            return this;
        }

        public Params withGroup(String group) {
            this.group = Optional.of(group);
            return this;
        }

        /**
         * @param permissions of the form "rwxr-x---".
         */
        public Params withPermissions(String permissions) {
            this.permissions = Optional.of(permissions);
            return this;
        }
    }

    public WriteFileTask(Params params) {
        this.params = params;
    }

    @Override
    public boolean execute(TaskContext context) {
        final FileSystemPath path = context.getFileSystem().withPath(params.path);
        if (path.isRegularFile()) {
            return false;
        }

        context.executeSubtask(new MakeDirectoryTask(params.path.getParent()).withParents());

        path.writeUtf8File(params.contentProducer.call())
                .setPermissions("rw-r--r--")
                .setOwner("root")
                .setGroup("root");

        // TODO: Only return true if file changed.
        return true;
    }
}
