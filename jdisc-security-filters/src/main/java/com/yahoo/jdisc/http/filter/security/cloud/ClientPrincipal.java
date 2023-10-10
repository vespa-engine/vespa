// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.jdisc.http.filter.security.cloud;

import com.yahoo.jdisc.http.filter.DiscFilterRequest;

import java.security.Principal;
import java.util.Set;
import java.util.logging.Logger;

/**
 * @author bjorncs
 */
record ClientPrincipal(Set<String> ids, Set<Permission> permissions) implements Principal {

    private static final Logger log = Logger.getLogger(ClientPrincipal.class.getName());

    ClientPrincipal { ids = Set.copyOf(ids); permissions = Set.copyOf(permissions); }
    @Override public String getName() {
        return "ids=%s,permissions=%s".formatted(ids, permissions.stream().map(Permission::asString).toList());
    }

    static void attachToRequest(DiscFilterRequest req, Set<String> ids, Set<Permission> permissions) {
        var p = new ClientPrincipal(ids, permissions);
        req.setUserPrincipal(p);
        log.fine(() -> "Client with ids=%s, permissions=%s"
                .formatted(ids, permissions.stream().map(Permission::asString).toList()));
    }
}

