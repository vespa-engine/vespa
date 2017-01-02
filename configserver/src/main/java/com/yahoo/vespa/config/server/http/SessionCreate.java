// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http;

import com.google.common.io.Files;
import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.TenantName;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.io.IOUtils;
import com.yahoo.log.LogLevel;
import com.yahoo.slime.Slime;
import com.yahoo.vespa.config.server.TimeoutBudget;
import com.yahoo.vespa.config.server.http.v2.SessionCreateResponse;
import com.yahoo.vespa.config.server.session.LocalSession;
import com.yahoo.vespa.config.server.session.LocalSessionRepo;
import com.yahoo.vespa.config.server.session.SessionFactory;

import java.io.File;
import java.io.IOException;

/**
 * Creates a session from an application package,
 * or creates a new session from a previous session (with id or the "active" session).
 *
 * @author lulf
 * @author hmusum
 * @since 5.1.27
 */
// TODO Rename class
public class SessionCreate {
    public final static String APPLICATION_X_GZIP = "application/x-gzip";
    public final static String APPLICATION_ZIP = "application/zip";
    public final static String contentTypeHeader = "Content-Type";

    private final SessionFactory sessionFactory;
    private final LocalSessionRepo localSessionRepo;
    private final SessionCreateResponse responseCreator;

    public SessionCreate(SessionFactory sessionFactory, LocalSessionRepo localSessionRepo, SessionCreateResponse responseCreator) {
        this.sessionFactory = sessionFactory;
        this.localSessionRepo = localSessionRepo;
        this.responseCreator = responseCreator;
    }

    public HttpResponse createFromExisting(HttpRequest request, Slime deployLog, LocalSession fromSession, TenantName tenant, TimeoutBudget timeoutBudget) {
        DeployLogger logger =  SessionHandler.createLogger(deployLog, request,
                                                                  new ApplicationId.Builder().tenant(tenant).applicationName("-").build());
        LocalSession session = sessionFactory.createSessionFromExisting(fromSession, logger, timeoutBudget);
        localSessionRepo.addSession(session);
        return createResponse(request, session);
    }

    public HttpResponse create(HttpRequest request, Slime deployLog, TenantName tenant, TimeoutBudget timeoutBudget) {
        validateDataAndHeader(request);
        return createSession(request, deployLog, sessionFactory, localSessionRepo, tenant, timeoutBudget);
    }

    private HttpResponse createSession(HttpRequest request, Slime deployLog, SessionFactory sessionFactory, LocalSessionRepo localSessionRepo, TenantName tenant, TimeoutBudget timeoutBudget) {
        File tempDir = Files.createTempDir();
        File applicationDirectory = decompressApplication(request, tempDir);
        DeployLogger logger = SessionHandler.createLogger(deployLog, request,
                                                          new ApplicationId.Builder().tenant(tenant).applicationName("-").build());
        String name = getNameProperty(request, logger);
        LocalSession session = sessionFactory.createSession(applicationDirectory, name, timeoutBudget);
        localSessionRepo.addSession(session);
        HttpResponse response = createResponse(request, session);
        cleanupApplicationDirectory(tempDir, logger);
        return response;
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
        try (CompressedApplicationInputStream application = CompressedApplicationInputStream.createFromCompressedStream(request.getData(), request.getHeader(contentTypeHeader))) {
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

    private HttpResponse createResponse(HttpRequest request, LocalSession session) {
        return responseCreator.createResponse(request.getHost(), request.getPort(), session.getSessionId());
    }
}
