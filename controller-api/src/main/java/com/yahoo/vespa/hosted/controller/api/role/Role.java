// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.role;

import java.net.URI;
import java.util.Objects;

import static java.util.Objects.requireNonNull;

/**
 * A role is a combination of a {@link ProtoRole} and a {@link Context}, which allows evaluation
 * of access control for a given action on a resource. Create using {@link Roles}.
 *
 * @author jonmv
 */
public abstract class Role {

    private final ProtoRole protoRole;
    final Context context;

    Role(ProtoRole protoRole, Context context) {
        this.protoRole = requireNonNull(protoRole);
        this.context = requireNonNull(context);
    }

    /** Returns the proto role of this role. */
    public ProtoRole proto() { return protoRole; }

    /** Returns whether this role is allowed to perform the given action on the given resource. */
    public boolean allows(Action action, URI uri) {
        return protoRole.policies().stream().anyMatch(policy -> policy.evaluate(action, uri, context));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Role role = (Role) o;
        return protoRole == role.protoRole &&
                Objects.equals(context, role.context);
    }

    @Override
    public int hashCode() {
        return Objects.hash(protoRole, context);
    }

}

