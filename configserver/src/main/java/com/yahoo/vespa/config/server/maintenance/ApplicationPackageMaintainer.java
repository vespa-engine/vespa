// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.maintenance;

import com.yahoo.config.FileReference;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.subscription.ConfigSourceSet;
import com.yahoo.jrt.Supervisor;
import com.yahoo.jrt.Transport;
import com.yahoo.vespa.config.ConnectionPool;
import com.yahoo.vespa.config.server.ApplicationRepository;
import com.yahoo.vespa.config.server.session.RemoteSession;
import com.yahoo.vespa.config.server.session.Session;
import com.yahoo.vespa.config.server.session.SessionRepository;
import com.yahoo.vespa.config.server.tenant.Tenant;
import com.yahoo.vespa.curator.Curator;
import com.yahoo.vespa.defaults.Defaults;
import com.yahoo.vespa.filedistribution.FileDistributionConnectionPool;
import com.yahoo.vespa.filedistribution.FileDownloader;
import com.yahoo.vespa.filedistribution.FileReferenceDownload;

import java.io.File;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Future;
import java.util.logging.Logger;

import static com.yahoo.vespa.config.server.filedistribution.FileDistributionUtil.fileReferenceExistsOnDisk;
import static com.yahoo.vespa.config.server.filedistribution.FileDistributionUtil.getOtherConfigServersInCluster;
import static com.yahoo.vespa.config.server.session.Session.Status.ACTIVATE;
import static com.yahoo.vespa.config.server.session.Session.Status.PREPARE;

/**
 * Verifies that all active sessions has an application package on local disk.
 * If not, the package is downloaded with file distribution. This can happen e.g.
 * if a config server is down when the application is deployed. This maintainer should only be run
 * if there is more than 1 config server
 *
 * @author gjoranv
 */
public class ApplicationPackageMaintainer extends ConfigServerMaintainer {

    private static final Logger log = Logger.getLogger(ApplicationPackageMaintainer.class.getName());

    private final File downloadDirectory;
    private final Supervisor supervisor = new Supervisor(new Transport("filedistribution-pool")).setDropEmptyBuffers(true);
    private final FileDownloader fileDownloader;

    ApplicationPackageMaintainer(ApplicationRepository applicationRepository, Curator curator, Duration interval) {
        super(applicationRepository, curator, applicationRepository.flagSource(), applicationRepository.clock(), interval, false);
        this.downloadDirectory = new File(Defaults.getDefaults().underVespaHome(applicationRepository.configserverConfig().fileReferencesDir()));
        this.fileDownloader = createFileDownloader(applicationRepository, downloadDirectory, supervisor);
    }

    @Override
    protected double maintain() {
        int attempts = 0;
        int[] failures = new int[1];

        List<Runnable> futureDownloads = new ArrayList<>();
        for (Session session : preparedAndActivatedSessions()) {
            if (shuttingDown())
                return asSuccessFactorDeviation(attempts, failures[0]);

            ApplicationId applicationId = session.getOptionalApplicationId().orElse(null);
            if (applicationId == null) // dry-run sessions have no application id
                continue;

            Optional<FileReference> appFileReference = session.getApplicationPackageReference();
            if (appFileReference.isPresent()) {
                long sessionId = session.getSessionId();
                FileReference fileReference = appFileReference.get();

                attempts++;
                if (! fileReferenceExistsOnDisk(downloadDirectory, fileReference)) {
                    Future<Optional<File>> futureDownload = startDownload(fileReference, sessionId, applicationId);
                    futureDownloads.add(() -> {
                        try {
                            if (futureDownload.get().isPresent()) {
                                createLocalSessionIfMissing(applicationId, sessionId);
                                return;
                            }
                        }
                        catch (Exception e) {
                            log.warning("Exception when downloading application package (" + fileReference + ")" +
                                                " for " + applicationId + " (session " + sessionId + "): " + e.getMessage());
                        }
                        failures[0]++;
                        log.info("Downloading application package (" + fileReference + ")" +
                                         " for " + applicationId + " (session " + sessionId + ") unsuccessful. " +
                                         "Can be ignored unless it happens many times over a long period of time, retries is expected");
                    });
                }
                else {
                    createLocalSessionIfMissing(applicationId, sessionId);
                }
            }
        }
        futureDownloads.forEach(Runnable::run);
        return asSuccessFactorDeviation(attempts, failures[0]);
    }

    private Future<Optional<File>> startDownload(FileReference fileReference, long sessionId, ApplicationId applicationId) {
        log.fine(() -> "Downloading application package with file reference " + fileReference +
                " for " + applicationId + " (session " + sessionId + ")");
        return fileDownloader.getFutureFileOrTimeout(new FileReferenceDownload(fileReference,
                                                                               this.getClass().getSimpleName(),
                                                                               false));
    }

    private Collection<RemoteSession> preparedAndActivatedSessions() {
        var tenantRepository = applicationRepository.tenantRepository();
        return tenantRepository.getAllTenantNames().stream()
                .map(tenantRepository::getTenant)
                .map(t -> t.getSessionRepository().getRemoteSessions())
                .flatMap(Collection::stream)
                .filter(s -> s.getStatus() == PREPARE || s.getStatus() == ACTIVATE)
                .toList();
    }

    private static FileDownloader createFileDownloader(ApplicationRepository applicationRepository,
                                                       File downloadDirectory,
                                                       Supervisor supervisor) {
        List<String> otherConfigServersInCluster = getOtherConfigServersInCluster(applicationRepository.configserverConfig());
        ConfigSourceSet configSourceSet = new ConfigSourceSet(otherConfigServersInCluster);
        ConnectionPool connectionPool = new FileDistributionConnectionPool(configSourceSet, supervisor);
        return new FileDownloader(connectionPool, supervisor, downloadDirectory, Duration.ofSeconds(60));
    }

    @Override
    public void awaitShutdown() {
        supervisor.transport().shutdown().join();
        fileDownloader.close();
        super.awaitShutdown();
    }

    private void createLocalSessionIfMissing(ApplicationId applicationId, long sessionId) {
        Tenant tenant = applicationRepository.getTenant(applicationId);
        SessionRepository sessionRepository = tenant.getSessionRepository();
        if (sessionRepository.getLocalSession(sessionId) == null)
            sessionRepository.createLocalSessionFromDistributedApplicationPackage(sessionId);
    }

}
