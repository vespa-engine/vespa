// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.tenant;

import com.yahoo.config.provision.TenantName;
import com.yahoo.vespa.hosted.controller.api.integration.organization.Contact;

import java.util.Optional;

/**
 * Represents an user tenant in hosted Vespa.
 *
 * @author mpolden
 */
public class UserTenant extends Tenant {

    /**
     * This should only be used by serialization.
     * Use {@link #create(String)}.
     * */
    public UserTenant(TenantName name, Optional<Contact> contact) {
        super(name, contact);
    }

    @Override
    public Type type() {
        return Type.user;
    }

    public UserTenant(TenantName name) {
        super(name, Optional.empty());
    }

    /** Returns true if this is the tenant for the given user name */
    public boolean is(String username) {
        return name().value().equals(normalizeUser(username));
    }

    @Override
    public String toString() {
        return "user tenant '" + name() + "'";
    }

    /** Create a new user tenant */
    public static UserTenant create(String username) {
        TenantName name = TenantName.from(username);
        return new UserTenant(requireName(requireUser(name)));
    }

    public static UserTenant create(String username, Optional<Contact> contact) {
        TenantName name = TenantName.from(username);
        return new UserTenant(requireName(requireUser(name)), contact);
    }

    /** Normalize given username. E.g. foo_bar becomes by-foo-bar */
    public static String normalizeUser(String username) {
        int offset = 0;
        if (username.startsWith(Tenant.userPrefix)) {
            offset = Tenant.userPrefix.length();
        }
        return Tenant.userPrefix + username.substring(offset).replace('_', '-');
    }

    private static TenantName requireUser(TenantName name) {
        if (!name.value().startsWith(Tenant.userPrefix)) {
            throw new IllegalArgumentException("User tenant must have prefix '" + Tenant.userPrefix + "'");
        }
        if (name.value().substring(Tenant.userPrefix.length()).contains("_")) {
            throw new IllegalArgumentException("User tenant cannot contain '_'");
        }
        return name;
    }

}
