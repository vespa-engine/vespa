// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.hosted.node.admin.task.util.file;

import java.util.Optional;

/**
 * Represents a subset of a file's content, owner, group, and permissions.
 *
 * @author hakonhall
 */
// @Immutable
public class PartialFileData {
    private final Optional<String> content;
    private final Optional<String> owner;
    private final Optional<String> group;
    private final Optional<String> permissions;

    public static Builder builder() {
        return new Builder();
    }

    public PartialFileData(Optional<String> content,
                           Optional<String> owner,
                           Optional<String> group,
                           Optional<String> permissions) {
        this.content = content;
        this.owner = owner;
        this.group = group;
        this.permissions = permissions;
    }

    public Optional<String> getContent() {
        return content;
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

    public static class Builder {
        private Optional<String> content = Optional.empty();
        private Optional<String> owner = Optional.empty();
        private Optional<String> group = Optional.empty();
        private Optional<String> permissions = Optional.empty();

        public Builder withContent(String content) { this.content = Optional.of(content); return this; }
        public Builder withOwner(String owner) { this.owner = Optional.of(owner); return this; }
        public Builder withGroup(String group) { this.group = Optional.of(group); return this; }
        public Builder withPermissions(String permissions) { this.permissions = Optional.of(permissions); return this; }

        public PartialFileData create() {
            return new PartialFileData(content, owner, group, permissions);
        }
    }
}
