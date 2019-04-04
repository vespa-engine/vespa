package com.yahoo.vespa.hosted.controller.restapi.filter;

import com.yahoo.jdisc.http.filter.DiscFilterRequest;
import com.yahoo.jdisc.http.filter.security.base.JsonSecurityRequestFilterBase;
import com.yahoo.vespa.hosted.controller.api.role.RoleMembership;
import com.yahoo.vespa.hosted.controller.api.role.RolePrincipal;

import java.util.Objects;
import java.util.Optional;

/**
 * Enriches the request principal with roles from Athenz.
 *
 * @author jonmv
 */
public class Auth0RoleFilter extends JsonSecurityRequestFilterBase {

    @Override
    protected Optional<ErrorResponse> filter(DiscFilterRequest request) {
        return Optional.empty();
    }


    private static class Auth0RolePrincipal implements RolePrincipal {

        private final String name;
        private final RoleMembership roles;

        public Auth0RolePrincipal(String name, RoleMembership roles) {
            if (name.isBlank()) throw new IllegalArgumentException("Name may not be blank.");
            this.name = name;
            this.roles = Objects.requireNonNull(roles);
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public RoleMembership roles() {
            return roles;
        }

    }

}
