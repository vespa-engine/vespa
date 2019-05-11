// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi.filter;

import ai.vespa.hosted.api.Method;
import ai.vespa.hosted.api.RequestVerifier;
import com.google.inject.Inject;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ApplicationName;
import com.yahoo.config.provision.TenantName;
import com.yahoo.jdisc.Response;
import com.yahoo.jdisc.http.filter.DiscFilterRequest;
import com.yahoo.jdisc.http.filter.security.base.JsonSecurityRequestFilterBase;
import com.yahoo.log.LogLevel;
import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.api.role.Role;
import com.yahoo.vespa.hosted.controller.api.role.RoleDefinition;
import com.yahoo.vespa.hosted.controller.api.role.SecurityContext;
import com.yahoo.yolean.Exceptions;

import java.security.Principal;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Assigns the {@link Role#buildService(TenantName, ApplicationName)} role to requests with a
 * Authorization header signature matching the public key of the indicated application.
 * Requests which already have a set of roles assigned to them are not modified.
 *
 * @author jonmv
 */
public class SignatureFilter extends JsonSecurityRequestFilterBase {

    private static final Logger logger = Logger.getLogger(SignatureFilter.class.getName());

    private final Controller controller;

    @Inject
    public SignatureFilter(Controller controller) {
        this.controller = controller;
    }

    @Override
    protected Optional<ErrorResponse> filter(DiscFilterRequest request) {
        if (   request.getAttribute(SecurityContext.ATTRIBUTE_NAME) == null
            && request.getHeader("X-Authorization") != null)
            try {
                ApplicationId id = ApplicationId.fromSerializedForm(request.getHeader("X-Key-Id"));
                boolean verified = controller.applications().get(id)
                                             .flatMap(Application::pemDeployKey)
                                             .map(key -> new RequestVerifier(key, controller.clock()))
                                             .map(verifier -> verifier.verify(Method.valueOf(request.getMethod()),
                                                                              request.getUri(),
                                                                              request.getHeader("X-Timestamp"),
                                                                              request.getHeader("X-Content-Hash"),
                                                                              request.getHeader("X-Authorization")))
                                             .orElse(false);

                if (verified) {
                    Principal principal = () -> "buildService@" + id.tenant() + "." + id.application();
                    request.setUserPrincipal(principal);
                    request.setRemoteUser(principal.getName());
                    request.setAttribute(SecurityContext.ATTRIBUTE_NAME,
                                         new SecurityContext(principal,
                                                             Set.of(Role.buildService(id.tenant(), id.application()),
                                                                    Role.applicationDeveloper(id.tenant(), id.application()))));
                }
            }
            catch (Exception e) {
                logger.log(LogLevel.DEBUG, () -> "Exception verifying signed request: " + Exceptions.toMessageString(e));
            }
        return Optional.empty();
    }

}
