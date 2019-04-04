// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi.filter;

import com.google.inject.Inject;
import com.yahoo.config.provision.SystemName;
import com.yahoo.jdisc.Response;
import com.yahoo.jdisc.http.HttpRequest;
import com.yahoo.jdisc.http.filter.DiscFilterRequest;
import com.yahoo.jdisc.http.filter.security.cors.CorsFilterConfig;
import com.yahoo.jdisc.http.filter.security.cors.CorsRequestFilterBase;
import com.yahoo.log.LogLevel;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.role.Action;
import com.yahoo.vespa.hosted.controller.role.Role;
import com.yahoo.vespa.hosted.controller.role.RoleMembership;
import com.yahoo.vespa.hosted.controller.role.RolePrincipal;

import java.security.Principal;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;

/**
 * A security filter protects all controller apis.
 *
 * @author bjorncs
 */
public class ControllerAuthorizationFilter extends CorsRequestFilterBase {

    private static final Logger log = Logger.getLogger(ControllerAuthorizationFilter.class.getName());

    private final SystemName system;

    @Inject
    public ControllerAuthorizationFilter(Controller controller,
                                         CorsFilterConfig corsConfig) {
        this(controller.system(), Set.copyOf(corsConfig.allowedUrls()));
    }

    ControllerAuthorizationFilter(SystemName system,
                                  Set<String> allowedUrls) {
        super(allowedUrls);
        this.system = system;
    }

    @Override
    public Optional<ErrorResponse> filterRequest(DiscFilterRequest request) {
        try {
            Principal principal = request.getUserPrincipal();
            if ( ! (principal instanceof RolePrincipal))
                return Optional.of(new ErrorResponse(Response.Status.FORBIDDEN, "Access denied"));

            Action action = Action.from(HttpRequest.Method.valueOf(request.getMethod()));

            // Avoid expensive lookups when request is always legal.
            if (Role.everyone.limitedTo(system).allows(action, request.getUri()))
                return Optional.empty();

            RoleMembership roles = ((RolePrincipal) principal).roles();
            if (roles.allows(action, request.getUri()))
                return Optional.empty();
        }
        catch (Exception e) {
            log.log(LogLevel.WARNING, "Exception evaluating access control: ", e);
        }
        return Optional.of(new ErrorResponse(Response.Status.FORBIDDEN, "Access denied"));
    }

}
