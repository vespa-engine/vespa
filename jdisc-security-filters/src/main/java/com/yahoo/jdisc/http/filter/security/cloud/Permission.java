// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.jdisc.http.filter.security.cloud;

import com.yahoo.container.jdisc.AclMapping;
import com.yahoo.container.jdisc.RequestHandlerSpec;
import com.yahoo.container.jdisc.RequestView;
import com.yahoo.jdisc.http.filter.DiscFilterRequest;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Optional;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * @author bjorncs
 */
enum Permission {
    READ, WRITE;

    private static final Logger log = Logger.getLogger(Permission.class.getName());

    String asString() {
        return switch (this) {
            case READ -> "read";
            case WRITE -> "write";
        };
    }

    static Permission of(String v) {
        return switch (v) {
            case "read" -> READ;
            case "write" -> WRITE;
            default -> throw new IllegalArgumentException("Invalid permission '%s'".formatted(v));
        };
    }

    static EnumSet<Permission> setOf(Collection<String> v) {
        return v.stream().map(Permission::of).collect(Collectors.toCollection(() -> EnumSet.noneOf(Permission.class)));
    }

    static Optional<Permission> getRequiredPermission(DiscFilterRequest req) {
        RequestView view = req.asRequestView();
        var result = Optional.ofNullable((RequestHandlerSpec) req.getAttribute(RequestHandlerSpec.ATTRIBUTE_NAME))
                .or(() -> Optional.of(RequestHandlerSpec.DEFAULT_INSTANCE))
                .flatMap(spec -> {
                    var action = spec.aclMapping().get(view);
                    var maybePermission = Permission.of(action);
                    if (maybePermission.isEmpty()) log.fine(() -> "Unknown action '%s'".formatted(action));
                    return maybePermission;
                });
        if (result.isEmpty())
            log.fine(() -> "No valid permission mapping defined for %s @ '%s'".formatted(view.method(), view.uri()));
        return result;
    }

    static Optional<Permission> of(AclMapping.Action a) {
        if (a.equals(AclMapping.Action.READ)) return Optional.of(READ);
        if (a.equals(AclMapping.Action.WRITE)) return Optional.of(WRITE);
        return Optional.empty();
    }
}
