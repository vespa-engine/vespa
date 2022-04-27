// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi.playground;

import com.yahoo.jdisc.http.filter.DiscFilterRequest;
import com.yahoo.jdisc.http.filter.security.base.JsonSecurityRequestFilterBase;
import com.yahoo.vespa.athenz.api.AthenzDomain;
import com.yahoo.vespa.athenz.api.AthenzPrincipal;
import com.yahoo.vespa.athenz.api.AthenzUser;
import com.yahoo.jdisc.http.filter.security.misc.User;
import com.yahoo.vespa.hosted.controller.api.role.Role;
import com.yahoo.vespa.hosted.controller.api.role.SecurityContext;
import com.yahoo.yolean.Exceptions;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

public class AllowingFilter extends JsonSecurityRequestFilterBase {

    static final AthenzPrincipal user = new AthenzPrincipal(new AthenzUser("demo"));
    static final AthenzDomain domain = new AthenzDomain("domain");

    @Override
    protected Optional<ErrorResponse> filter(DiscFilterRequest request) {
        try {
            request.setUserPrincipal(user);
            request.setAttribute(User.ATTRIBUTE_NAME, new User("mail@mail", user.getName(), "demo", null, true, -1, User.NO_DATE));
            request.setAttribute("okta.identity-token", "okta-it");
            request.setAttribute("okta.access-token", "okta-at");
            request.setAttribute(SecurityContext.ATTRIBUTE_NAME,
                                 new SecurityContext(user,
                                                     Set.of(Role.hostedOperator()),
                                                     Instant.now().minusSeconds(3600)));
            return Optional.empty();
        }
        catch (Throwable t) {
            return Optional.of(new ErrorResponse(500, Exceptions.toMessageString(t)));
        }
    }

}
