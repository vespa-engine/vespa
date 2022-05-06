// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi.filter;

import com.yahoo.component.annotation.Inject;
import com.yahoo.jdisc.Response;
import com.yahoo.jdisc.http.HttpRequest;
import com.yahoo.jdisc.http.filter.DiscFilterRequest;
import com.yahoo.jdisc.http.filter.security.base.JsonSecurityRequestFilterBase;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.api.role.Action;
import com.yahoo.vespa.hosted.controller.api.role.Enforcer;
import com.yahoo.vespa.hosted.controller.api.role.Role;
import com.yahoo.vespa.hosted.controller.api.role.SecurityContext;

import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A security filter protects all controller apis.
 *
 * @author bjorncs
 */
public class ControllerAuthorizationFilter extends JsonSecurityRequestFilterBase {

    private static final Logger log = Logger.getLogger(ControllerAuthorizationFilter.class.getName());

    private final Enforcer enforcer;

    @Inject
    public ControllerAuthorizationFilter(Controller controller) {
        this.enforcer = new Enforcer(controller.system());
    }

    @Override
    public Optional<ErrorResponse> filter(DiscFilterRequest request) {
        try {
            Optional<SecurityContext> securityContext = Optional.ofNullable((SecurityContext)request.getAttribute(SecurityContext.ATTRIBUTE_NAME));

            if (securityContext.isEmpty())
                return Optional.of(new ErrorResponse(Response.Status.UNAUTHORIZED, "Access denied - not authenticated"));

            Action action = Action.from(HttpRequest.Method.valueOf(request.getMethod()));

            // Avoid expensive look-ups when request is always legal.
            if (enforcer.allows(Role.everyone(), action, request.getUri()))
                return Optional.empty();

            Set<Role> roles = securityContext.get().roles();
            if (roles.stream().anyMatch(role -> enforcer.allows(role, action, request.getUri())))
                return Optional.empty();
        }
        catch (Exception e) {
            log.log(Level.WARNING, "Exception evaluating access control: ", e);
        }
        return Optional.of(new ErrorResponse(Response.Status.FORBIDDEN, "Access denied"));
    }

}
