package com.yahoo.vespa.hosted.controller.restapi.filter;

import ai.vespa.hosted.api.Method;
import ai.vespa.hosted.api.RequestVerifier;
import com.google.inject.Inject;
import com.yahoo.config.provision.ApplicationId;
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
 * Accepts or rejects HTTP requests based on whether a signature header matches an implied public key.
 *
 * Requests that are approved are enriched with a {@link RoleDefinition#buildService} role.
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
                request.setAttribute(SecurityContext.ATTRIBUTE_NAME,
                                     new SecurityContext(() -> "buildService@" + id.tenant() + "." + id.application(),
                                                         Set.of(Role.buildService(id.tenant(), id.application()))));
                return Optional.empty();
            }
        }
        catch (Exception e) {
            logger.log(LogLevel.DEBUG, () -> "Exception verifying signed request: " + Exceptions.toMessageString(e));
        }
        return Optional.of(new ErrorResponse(Response.Status.UNAUTHORIZED, "Access denied"));
    }



}
