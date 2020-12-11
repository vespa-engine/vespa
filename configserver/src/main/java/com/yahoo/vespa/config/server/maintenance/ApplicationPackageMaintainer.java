package com.yahoo.vespa.config.server.maintenance;

import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.config.FileReference;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.config.ConnectionPool;
import com.yahoo.vespa.config.server.ApplicationRepository;
import com.yahoo.vespa.config.server.session.Session;
import com.yahoo.vespa.config.server.session.SessionRepository;
import com.yahoo.vespa.config.server.tenant.Tenant;
import com.yahoo.vespa.curator.Curator;
import com.yahoo.vespa.defaults.Defaults;
import com.yahoo.vespa.filedistribution.FileDownloader;
import com.yahoo.vespa.flags.FlagSource;

import java.io.File;
import java.time.Duration;
import java.util.logging.Logger;

import static com.yahoo.vespa.config.server.filedistribution.FileDistributionUtil.createConnectionPool;
import static com.yahoo.vespa.config.server.filedistribution.FileDistributionUtil.fileReferenceExistsOnDisk;

/**
 * Verifies that all active sessions has an application package on local disk.
 * If not, the package is downloaded with file distribution. This can happen e.g.
 * if a configserver is down when the application is deployed.
 *
 * @author gjoranv
 */
public class ApplicationPackageMaintainer extends ConfigServerMaintainer {
    private static final Logger log = Logger.getLogger(ApplicationPackageMaintainer.class.getName());

    private final ApplicationRepository applicationRepository;
    private final ConnectionPool connectionPool;
    private final File downloadDirectory;

    ApplicationPackageMaintainer(ApplicationRepository applicationRepository,
                                 Curator curator,
                                 Duration interval,
                                 FlagSource flagSource) {
        super(applicationRepository, curator, flagSource, applicationRepository.clock().instant(), interval);
        this.applicationRepository = applicationRepository;
        ConfigserverConfig configserverConfig = applicationRepository.configserverConfig();
        connectionPool = createConnectionPool(configserverConfig);

        downloadDirectory = new File(Defaults.getDefaults().underVespaHome(configserverConfig.fileReferencesDir()));
    }

    @Override
    protected boolean maintain() {
        boolean success = true;

        try (var fileDownloader = new FileDownloader(connectionPool, downloadDirectory)) {
            for (var applicationId : applicationRepository.listApplications()) {
                log.fine(() -> "Verifying application package for " + applicationId);
                Session session = applicationRepository.getActiveSession(applicationId);
                if (session == null) continue;  // App might be deleted after call to listApplications()

                FileReference applicationPackage = session.getApplicationPackageReference();
                long sessionId = session.getSessionId();
                log.fine(() -> "Verifying application package file reference " + applicationPackage + " for session " + sessionId);

                if (applicationPackage != null) {
                    if (! fileReferenceExistsOnDisk(downloadDirectory, applicationPackage)) {
                        log.fine(() -> "Downloading missing application package for application " + applicationId + " - session " + sessionId);

                        if (fileDownloader.getFile(applicationPackage).isEmpty()) {
                            success = false;
                            log.warning("Failed to download application package for application " + applicationId + " - session " + sessionId);
                            continue;
                        }
                    }
                    createLocalSessionIfMissing(applicationId, sessionId);
                }
            }
        }
        return success;
    }

    @Override
    public void awaitShutdown() {
        connectionPool.close();
        super.awaitShutdown();
    }

    private void createLocalSessionIfMissing(ApplicationId applicationId, long sessionId) {
        Tenant tenant = applicationRepository.getTenant(applicationId);
        SessionRepository sessionRepository = tenant.getSessionRepository();
        if (sessionRepository.getLocalSession(sessionId) == null)
            sessionRepository.createLocalSessionFromDistributedApplicationPackage(sessionId);
    }

}
