// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi.filter;

import com.google.inject.Inject;
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
import com.yahoo.yolean.chain.After;
import com.yahoo.yolean.chain.Provides;

import java.security.Principal;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;

/**
 * A security filter protects all controller apis.
 *
 * @author bjorncs
 */
@After("com.yahoo.vespa.hosted.controller.athenz.filter.UserAuthWithAthenzPrincipalFilter")
@Provides("ControllerAuthorizationFilter")
public class ControllerAuthorizationFilter extends CorsRequestFilterBase {

    private static final Logger log = Logger.getLogger(ControllerAuthorizationFilter.class.getName());

    private final RoleMembership.Resolver roleResolver;
    private final Controller controller;

    @Inject
    public ControllerAuthorizationFilter(RoleMembership.Resolver roleResolver,
                                         Controller controller,
                                         CorsFilterConfig corsConfig) {
        this(roleResolver, controller, Set.copyOf(corsConfig.allowedUrls()));
    }

    ControllerAuthorizationFilter(RoleMembership.Resolver roleResolver,
                                  Controller controller,
                                  Set<String> allowedUrls) {
        super(allowedUrls);
        this.roleResolver = roleResolver;
        this.controller = controller;
    }

    @Override
    public Optional<ErrorResponse> filterRequest(DiscFilterRequest request) {
        try {
            Principal principal = request.getUserPrincipal();
            if (principal == null)
                return Optional.of(new ErrorResponse(Response.Status.FORBIDDEN, "Access denied"));

            Action action = Action.from(HttpRequest.Method.valueOf(request.getMethod()));

            // Avoid expensive lookups when request is always legal.
            if (Role.everyone.limitedTo(controller.system()).allows(action, request.getRequestURI()))
                return Optional.empty();

            RoleMembership roles = this.roleResolver.membership(principal, Optional.of(request.getRequestURI()));
            if (roles.allows(action, request.getRequestURI()))
                return Optional.empty();
        }
        catch (Exception e) {
            log.log(LogLevel.WARNING, "Exception evaluating access control: ", e);
        }
        return Optional.of(new ErrorResponse(Response.Status.FORBIDDEN, "Access denied"));
    }

}
