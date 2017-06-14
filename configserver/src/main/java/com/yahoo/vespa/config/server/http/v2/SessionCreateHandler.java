// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http.v2;

import com.google.common.io.Files;
import com.google.inject.Inject;
import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.TenantName;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.container.logging.AccessLog;
import com.yahoo.io.IOUtils;
import com.yahoo.jdisc.application.UriPattern;
import com.yahoo.log.LogLevel;
import com.yahoo.slime.Slime;
import com.yahoo.vespa.config.server.ApplicationRepository;
import com.yahoo.vespa.config.server.deploy.DeployHandlerLogger;
import com.yahoo.vespa.config.server.http.CompressedApplicationInputStream;
import com.yahoo.vespa.config.server.http.InternalServerException;
import com.yahoo.vespa.config.server.tenant.Tenant;
import com.yahoo.vespa.config.server.tenant.Tenants;
import com.yahoo.vespa.config.server.TimeoutBudget;
import com.yahoo.vespa.config.server.http.BadRequestException;
import com.yahoo.vespa.config.server.http.SessionHandler;
import com.yahoo.vespa.config.server.http.Utils;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.concurrent.Executor;

/**
 * A handler that is able to create a session from an application package,
 * or create a new session from a previous session (with id or the "active" session).
 * Handles /application/v2/ requests
 *
 * @author hmusum
 * @since 5.1
 */
public class SessionCreateHandler extends SessionHandler {
    public final static String APPLICATION_X_GZIP = "application/x-gzip";
    public final static String APPLICATION_ZIP = "application/zip";
    public final static String contentTypeHeader = "Content-Type";
    private static final String fromPattern = "http://*/application/v2/tenant/*/application/*/environment/*/region/*/instance/*";

    private final Tenants tenants;
    private final Duration zookeeperBarrierTimeout;

    @Inject
    public SessionCreateHandler(Executor executor,
                                AccessLog accessLog,
                                Tenants tenants,
                                ConfigserverConfig configserverConfig,
                                ApplicationRepository applicationRepository) {
        super(executor, accessLog, applicationRepository);
        this.tenants = tenants;
        this.zookeeperBarrierTimeout = Duration.ofSeconds(configserverConfig.zookeeper().barrierTimeout());
    }

    @Override
    protected HttpResponse handlePOST(HttpRequest request) {
        Slime deployLog = createDeployLog();
        final TenantName tenantName = Utils.getTenantNameFromSessionRequest(request);
        Utils.checkThatTenantExists(tenants, tenantName);
        Tenant tenant = tenants.getTenant(tenantName);
        TimeoutBudget timeoutBudget = SessionHandler.getTimeoutBudget(request, zookeeperBarrierTimeout);
        DeployLogger logger = createLogger(request, deployLog, tenantName);
        long sessionId;
        if (request.hasProperty("from")) {
            ApplicationId applicationId = getFromApplicationId(request);
            sessionId = applicationRepository.createSessionFromExisting(tenant, logger, timeoutBudget, applicationId);
        } else {
            validateDataAndHeader(request);
            File tempDir = Files.createTempDir();
            File applicationDirectory = decompressApplication(request, tempDir);
            String name = getNameProperty(request, logger);
            sessionId = applicationRepository.createSession(tenant, timeoutBudget, applicationDirectory, name);
            cleanupApplicationDirectory(tempDir, logger);
        }
        return createResponse(request, tenantName, deployLog, sessionId);
    }

    private static ApplicationId getFromApplicationId(HttpRequest request) {
        String from = request.getProperty("from");
        if (from == null || "".equals(from)) {
            throw new BadRequestException("Parameter 'from' has illegal value '" + from + "'");
        }
        return getAndValidateFromParameter(URI.create(from));
    }

    private static ApplicationId getAndValidateFromParameter(URI from) {
        UriPattern.Match match = new UriPattern(fromPattern).match(from);
        if (match == null || match.groupCount() < 7) {
            throw new BadRequestException("Parameter 'from' has illegal value '" + from + "'");
        }
        return new ApplicationId.Builder()
            .tenant(match.group(2))
            .applicationName(match.group(3))
            .instanceName(match.group(6)).build();
    }

    private DeployHandlerLogger createLogger(HttpRequest request, Slime deployLog, TenantName tenant) {
        return SessionHandler.createLogger(deployLog, request,
                                           new ApplicationId.Builder().tenant(tenant).applicationName("-").build());
    }

    private String getNameProperty(HttpRequest request, DeployLogger logger) {
        String name = request.getProperty("name");
        // TODO: Do we need validation of this parameter?
        if (name == null) {
            name = "default";
            logger.log(LogLevel.INFO, "No application name given, using '" + name + "'");
        }
        return name;
    }

    private File decompressApplication(HttpRequest request, File tempDir) {
        try (CompressedApplicationInputStream application = CompressedApplicationInputStream.createFromCompressedStream(request.getData(), request
                .getHeader(contentTypeHeader))) {
            return application.decompress(tempDir);
        } catch (IOException e) {
            throw new InternalServerException("Unable to decompress data in body", e);
        }
    }

    private void cleanupApplicationDirectory(File tempDir, DeployLogger logger) {
        logger.log(LogLevel.DEBUG, "Deleting tmp dir '" + tempDir + "'");
        if (!IOUtils.recursiveDeleteDir(tempDir)) {
            logger.log(LogLevel.WARNING, "Not able to delete tmp dir '" + tempDir + "'");
        }
    }

    private static void validateDataAndHeader(HttpRequest request) {
        if (request.getData() == null) {
            throw new BadRequestException("Request contains no data");
        }
        String header = request.getHeader(contentTypeHeader);
        if (header == null) {
            throw new BadRequestException("Request contains no " + contentTypeHeader + " header");
        } else if (!(header.equals(APPLICATION_X_GZIP) || header.equals(APPLICATION_ZIP))) {
            throw new BadRequestException("Request contains invalid " + contentTypeHeader + " header, only '" +
                                                  APPLICATION_X_GZIP + "' and '" + APPLICATION_ZIP + "' are supported");
        }
    }

    private HttpResponse createResponse(HttpRequest request, TenantName tenantName, Slime deployLog, long sessionId) {
        return new SessionCreateResponse(tenantName, deployLog, deployLog.get())
                .createResponse(request.getHost(), request.getPort(), sessionId);
    }
}
