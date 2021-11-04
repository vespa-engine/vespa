// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.nodeagent;

import com.yahoo.vespa.hosted.node.admin.task.util.file.UnixUser;

import java.util.Objects;

/**
 * @author freva
 */
public class UserScope {

    private final UnixUser root;
    private final UnixUser vespa;
    private final UserNamespace namespace;

    private UserScope(UnixUser root, UnixUser vespa, UserNamespace namespace) {
        this.root = Objects.requireNonNull(root);
        this.vespa = Objects.requireNonNull(vespa);
        this.namespace = Objects.requireNonNull(namespace);
    }

    public UnixUser root() {
        return root;
    }

    public UnixUser vespa() {
        return vespa;
    }

    public UserNamespace namespace() {
        return namespace;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UserScope userScope = (UserScope) o;
        return root.equals(userScope.root) && vespa.equals(userScope.vespa) && namespace.equals(userScope.namespace);
    }

    @Override
    public int hashCode() {
        return Objects.hash(root, vespa, namespace);
    }

    /** Creates user scope with default root user */
    public static UserScope create(UnixUser vespaUser, UserNamespace namespace) {
        return new UserScope(UnixUser.ROOT, vespaUser, namespace);
    }
}
