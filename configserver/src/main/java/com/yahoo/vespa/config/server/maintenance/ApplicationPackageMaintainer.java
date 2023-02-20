// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.maintenance;

import com.yahoo.config.FileReference;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.subscription.ConfigSourceSet;
import com.yahoo.jrt.Supervisor;
import com.yahoo.jrt.Transport;
import com.yahoo.vespa.config.ConnectionPool;
import com.yahoo.vespa.config.server.ApplicationRepository;
import com.yahoo.vespa.config.server.session.Session;
import com.yahoo.vespa.config.server.session.SessionRepository;
import com.yahoo.vespa.config.server.tenant.Tenant;
import com.yahoo.vespa.curator.Curator;
import com.yahoo.vespa.defaults.Defaults;
import com.yahoo.vespa.filedistribution.FileDistributionConnectionPool;
import com.yahoo.vespa.filedistribution.FileDownloader;
import com.yahoo.vespa.filedistribution.FileReferenceDownload;
import com.yahoo.vespa.flags.FlagSource;
import java.io.File;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

import static com.yahoo.vespa.config.server.filedistribution.FileDistributionUtil.fileReferenceExistsOnDisk;

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

    private final ApplicationRepository applicationRepository;
    private final File downloadDirectory;
    private final Supervisor supervisor = new Supervisor(new Transport("filedistribution-pool")).setDropEmptyBuffers(true);
    private final FileDownloader fileDownloader;

    ApplicationPackageMaintainer(ApplicationRepository applicationRepository,
                                 Curator curator,
                                 Duration interval,
                                 FlagSource flagSource,
                                 List<String> otherConfigServersInCluster) {
        super(applicationRepository, curator, flagSource, applicationRepository.clock(), interval, false);
        this.applicationRepository = applicationRepository;
        this.downloadDirectory = new File(Defaults.getDefaults().underVespaHome(applicationRepository.configserverConfig().fileReferencesDir()));
        this.fileDownloader = createFileDownloader(otherConfigServersInCluster, downloadDirectory, supervisor);
    }

    @Override
    protected double maintain() {
        int attempts = 0;
        int failures = 0;

        for (var applicationId : applicationRepository.listApplications()) {
            if (shuttingDown())
                break;
            
            log.finest(() -> "Verifying application package for " + applicationId);
            Optional<Session> session = applicationRepository.getActiveSession(applicationId);
            if (session.isEmpty()) continue; // App might be deleted after call to listApplications() or not activated yet (bootstrap phase)

            FileReference appFileReference = session.get().getApplicationPackageReference();
            if (appFileReference != null) {
                long sessionId = session.get().getSessionId();
                attempts++;
                if (!fileReferenceExistsOnDisk(downloadDirectory, appFileReference)) {
                    log.fine(() -> "Downloading application package with file reference " + appFileReference +
                            " for " + applicationId + " (session " + sessionId + ")");

                    FileReferenceDownload download = new FileReferenceDownload(appFileReference,
                                                                               this.getClass().getSimpleName(),
                                                                               false);
                    if (fileDownloader.getFile(download).isEmpty()) {
                        failures++;
                        log.info("Downloading application package (" + appFileReference + ")" +
                                         " for " + applicationId + " (session " + sessionId + ") unsuccessful. " +
                                         "Can be ignored unless it happens many times over a long period of time, retries is expected");
                        continue;
                    }
                }
                createLocalSessionIfMissing(applicationId, sessionId);
            }
        }
        return asSuccessFactor(attempts, failures);
    }

    private static FileDownloader createFileDownloader(List<String> otherConfigServersInCluster,
                                                       File downloadDirectory,
                                                       Supervisor supervisor) {
        ConfigSourceSet configSourceSet = new ConfigSourceSet(otherConfigServersInCluster);
        ConnectionPool connectionPool = new FileDistributionConnectionPool(configSourceSet, supervisor);
        return new FileDownloader(connectionPool, supervisor, downloadDirectory, Duration.ofSeconds(300));
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
