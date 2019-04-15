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
import com.yahoo.vespa.hosted.controller.api.role.Action;
import com.yahoo.vespa.hosted.controller.api.role.Enforcer;
import com.yahoo.vespa.hosted.controller.api.role.Role;
import com.yahoo.vespa.hosted.controller.api.role.SecurityContext;

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

    private final Enforcer enforcer;

    @Inject
    public ControllerAuthorizationFilter(Controller controller,
                                         CorsFilterConfig corsConfig) {
        this(controller.system(), Set.copyOf(corsConfig.allowedUrls()));
    }

    ControllerAuthorizationFilter(SystemName system,
                                  Set<String> allowedUrls) {
        super(allowedUrls);
        this.enforcer = new Enforcer(system);
    }

    @Override
    public Optional<ErrorResponse> filterRequest(DiscFilterRequest request) {
        try {
            Optional<SecurityContext> securityContext = Optional.ofNullable((SecurityContext)request.getAttribute(SecurityContext.ATTRIBUTE_NAME));

            if (securityContext.isEmpty())
                return Optional.of(new ErrorResponse(Response.Status.FORBIDDEN, "Access denied"));

            Action action = Action.from(HttpRequest.Method.valueOf(request.getMethod()));

            // Avoid expensive look-ups when request is always legal.
            if (Role.everyone().allows(action, request.getUri(), enforcer))
                return Optional.empty();

            Set<Role> roles = securityContext.get().roles();
            if (roles.stream().anyMatch(role -> role.allows(action, request.getUri(), enforcer)))
                return Optional.empty();
        }
        catch (Exception e) {
            log.log(LogLevel.WARNING, "Exception evaluating access control: ", e);
        }
        return Optional.of(new ErrorResponse(Response.Status.FORBIDDEN, "Access denied"));
    }

}
