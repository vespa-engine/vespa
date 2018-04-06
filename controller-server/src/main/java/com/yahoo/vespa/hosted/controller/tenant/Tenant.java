// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.tenant;


import com.yahoo.config.provision.TenantName;

import java.util.Objects;

/**
 * A tenant in hosted Vespa.
 *
 * @author mpolden
 */
public abstract class Tenant {

    public static final String userPrefix = "by-";

    private final TenantName name;

    Tenant(TenantName name) {
        this.name = name;
    }

    /** Name of this tenant */
    public TenantName name() {
        return name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Tenant tenant = (Tenant) o;
        return Objects.equals(name, tenant.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }

    static TenantName requireName(TenantName name) {
        if (!name.value().matches("^(?=.{1,20}$)[a-z](-?[a-z0-9]+)*$")) {
            throw new IllegalArgumentException("New tenant or application names must start with a letter, may " +
                                               "contain no more than 20 characters, and may only contain lowercase " +
                                               "letters, digits or dashes, but no double-dashes.");
        }
        return name;
    }
}
