// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.tenant;

import com.yahoo.config.provision.TenantName;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Represents a tenant that has been deleted. Exists to prevent creation of a new tenant with the same name.
 *
 * @author freva
 */
public class DeletedTenant extends Tenant {

    private final Instant deletedAt;

    public DeletedTenant(TenantName name, Instant createdAt, Instant deletedAt) {
        super(name, createdAt, LastLoginInfo.EMPTY, Optional.empty());
        this.deletedAt = Objects.requireNonNull(deletedAt, "deletedAt must be non-null");
    }

    /** Instant when the tenant was deleted */
    public Instant deletedAt() {
        return deletedAt;
    }

    @Override
    public String toString() {
        return "deleted tenant '" + name() + "'";
    }

    @Override
    public Type type() {
        return Type.deleted;
    }

}
