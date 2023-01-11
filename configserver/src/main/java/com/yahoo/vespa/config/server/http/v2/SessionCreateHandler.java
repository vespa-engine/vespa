// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http.v2;

import com.yahoo.component.annotation.Inject;
import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ApplicationName;
import com.yahoo.config.provision.InstanceName;
import com.yahoo.config.provision.TenantName;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.jdisc.application.UriPattern;
import com.yahoo.vespa.config.server.ApplicationRepository;
import com.yahoo.vespa.config.server.deploy.DeployHandlerLogger;
import com.yahoo.vespa.config.server.TimeoutBudget;
import com.yahoo.vespa.config.server.http.BadRequestException;
import com.yahoo.vespa.config.server.http.SessionHandler;
import com.yahoo.vespa.config.server.http.Utils;
import com.yahoo.vespa.config.server.http.v2.response.SessionCreateResponse;
import org.apache.hc.core5.http.ContentType;

import java.net.URI;
import java.time.Duration;
import java.util.List;

/**
 * A handler that is able to create a session from an application package,
 * or create a new session from a previous session (with id or the "active" session).
 * Handles /application/v2/ requests
 *
 * @author hmusum
 */
public class SessionCreateHandler extends SessionHandler {

    private static final String fromPattern = "http://*/application/v2/tenant/*/application/*/environment/*/region/*/instance/*";

    private final Duration zookeeperBarrierTimeout;

    @Inject
    public SessionCreateHandler(Context ctx,
                                ApplicationRepository applicationRepository,
                                ConfigserverConfig configserverConfig) {
        super(ctx, applicationRepository);
        this.zookeeperBarrierTimeout = Duration.ofSeconds(configserverConfig.zookeeper().barrierTimeout());
    }

    @Override
    protected HttpResponse handlePOST(HttpRequest request) {
        TenantName tenantName = Utils.getTenantNameFromSessionRequest(request);
        Utils.checkThatTenantExists(applicationRepository.tenantRepository(), tenantName);
        TimeoutBudget timeoutBudget = SessionHandler.getTimeoutBudget(request, zookeeperBarrierTimeout);
        boolean verbose = request.getBooleanProperty("verbose");

        DeployHandlerLogger logger;
        long sessionId;
        if (request.hasProperty("from")) {
            ApplicationId applicationId = getFromApplicationId(request);
            logger = DeployHandlerLogger.forApplication(applicationId, verbose);
            sessionId = applicationRepository.createSessionFromExisting(applicationId, false, timeoutBudget, logger);
        } else {
            validateDataAndHeader(request, List.of(ApplicationApiHandler.APPLICATION_ZIP, ApplicationApiHandler.APPLICATION_X_GZIP));
            logger = DeployHandlerLogger.forTenant(tenantName, verbose);
            // TODO: Avoid using application id here at all
            ApplicationId applicationId = ApplicationId.from(tenantName, ApplicationName.defaultName(), InstanceName.defaultName());
            sessionId = applicationRepository.createSession(applicationId,
                                                            timeoutBudget,
                                                            request.getData(),
                                                            request.getHeader(ApplicationApiHandler.contentTypeHeader),
                                                            logger);
        }
        return new SessionCreateResponse(logger.slime(), tenantName, request.getHost(), request.getPort(), sessionId);
    }

    static ApplicationId getFromApplicationId(HttpRequest request) {
        String from = request.getProperty("from");
        if (from == null || "".equals(from))
            throw new BadRequestException("Parameter 'from' has illegal value '" + from + "'");

        return getAndValidateFromParameter(URI.create(from));
    }

    private static ApplicationId getAndValidateFromParameter(URI from) {
        UriPattern.Match match = new UriPattern(fromPattern).match(from);
        if (match == null || match.groupCount() < 7)
            throw new BadRequestException("Parameter 'from' has illegal value '" + from + "'");

        return new ApplicationId.Builder()
            .tenant(match.group(2))
            .applicationName(match.group(3))
            .instanceName(match.group(6)).build();
    }

    static void validateDataAndHeader(HttpRequest request, List<String> supportedContentTypes) {
        if (request.getData() == null)
            throw new BadRequestException("Request contains no data");

        String header = request.getHeader(ApplicationApiHandler.contentTypeHeader);
        if (header == null)
            throw new BadRequestException("Request contains no " + ApplicationApiHandler.contentTypeHeader + " header");

        ContentType contentType = ContentType.parse(header);
        if ( ! supportedContentTypes.contains(contentType.getMimeType()))
            throw new BadRequestException("Request contains invalid " + ApplicationApiHandler.contentTypeHeader +
                                                  " header (" + contentType.getMimeType() + "), only '[" +
                                                  String.join(", ", supportedContentTypes) + "]' are supported");
    }

}
