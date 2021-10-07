// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.task.util.file;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * Represents a subset of a file's content, owner, group, and permissions.
 *
 * @author hakonhall
 */
// @Immutable
public class PartialFileData {
    private final Optional<byte[]> content;
    private final Optional<Integer> ownerId;
    private final Optional<Integer> groupId;
    private final Optional<String> permissions;

    public static Builder builder() {
        return new Builder();
    }

    private PartialFileData(Optional<byte[]> content,
                            Optional<Integer> ownerId,
                            Optional<Integer> groupId,
                            Optional<String> permissions) {
        this.content = content;
        this.ownerId = ownerId;
        this.groupId = groupId;
        this.permissions = permissions;
    }

    public Optional<byte[]> getContent() {
        return content;
    }

    public Optional<Integer> getOwnerId() {
        return ownerId;
    }

    public Optional<Integer> getGroupId() {
        return groupId;
    }

    public Optional<String> getPermissions() {
        return permissions;
    }

    public static class Builder {
        private Optional<byte[]> content = Optional.empty();
        private Optional<Integer> ownerId = Optional.empty();
        private Optional<Integer> groupId = Optional.empty();
        private Optional<String> permissions = Optional.empty();

        public Builder withContent(byte[] content) { this.content = Optional.of(content); return this; }
        public Builder withContent(String content, Charset charset) { return withContent(content.getBytes(charset)); }
        public Builder withContent(String content) { return withContent(content, StandardCharsets.UTF_8); }
        public Builder withOwnerId(int ownerId) { this.ownerId = Optional.of(ownerId); return this; }
        public Builder withGroupId(int groupId) { this.groupId = Optional.of(groupId); return this; }
        public Builder withPermissions(String permissions) { this.permissions = Optional.of(permissions); return this; }

        public PartialFileData create() {
            return new PartialFileData(content, ownerId, groupId, permissions);
        }
    }
}
