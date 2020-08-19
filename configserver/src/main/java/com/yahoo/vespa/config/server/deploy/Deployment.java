// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.deploy;

import com.yahoo.component.Version;
import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.AthenzDomain;
import com.yahoo.config.provision.DockerImage;
import com.yahoo.config.provision.HostFilter;
import com.yahoo.config.provision.Provisioner;
import java.util.logging.Level;
import com.yahoo.vespa.config.server.ApplicationRepository;
import com.yahoo.vespa.config.server.ApplicationRepository.ActionTimer;
import com.yahoo.vespa.config.server.TimeoutBudget;
import com.yahoo.vespa.config.server.application.ApplicationSet;
import com.yahoo.vespa.config.server.http.InternalServerException;
import com.yahoo.vespa.config.server.session.LocalSession;
import com.yahoo.vespa.config.server.session.PrepareParams;
import com.yahoo.vespa.config.server.session.RemoteSession;
import com.yahoo.vespa.config.server.session.Session;
import com.yahoo.vespa.config.server.session.SilentDeployLogger;
import com.yahoo.vespa.config.server.tenant.Tenant;
import com.yahoo.vespa.curator.Lock;

import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import java.util.logging.Logger;

import static com.yahoo.vespa.curator.Curator.CompletionWaiter;

/**
 * The process of deploying an application.
 * Deployments are created by an {@link ApplicationRepository}.
 * Instances of this are not multithread safe.
 *
 * @author Ulf Lilleengen
 * @author bratseth
 */
public class Deployment implements com.yahoo.config.provision.Deployment {

    private static final Logger log = Logger.getLogger(Deployment.class.getName());

    /** The session containing the application instance to activate */
    private final LocalSession session;
    private final ApplicationRepository applicationRepository;
    private final Optional<Provisioner> hostProvisioner;
    private final Tenant tenant;
    private final Duration timeout;
    private final Clock clock;
    private final DeployLogger logger = new SilentDeployLogger();

    /** The repository part of docker image this application should run on. Version is separate from image repo */
    final Optional<DockerImage> dockerImageRepository;

    /** The Vespa version this application should run on */
    private final Version version;

    /** True if this deployment is done to bootstrap the config server */
    private final boolean isBootstrap;

    /** The (optional) Athenz domain this application should use */
    private final Optional<AthenzDomain> athenzDomain;

    private boolean prepared = false;
    
    /** Whether this model should be validated (only takes effect if prepared=false) */
    private final boolean validate;

    private boolean ignoreSessionStaleFailure = false;

    private Deployment(LocalSession session, ApplicationRepository applicationRepository,
                       Optional<Provisioner> hostProvisioner, Tenant tenant, Duration timeout,
                       Clock clock, boolean prepared, boolean validate, boolean isBootstrap) {
        this.session = session;
        this.applicationRepository = applicationRepository;
        this.hostProvisioner = hostProvisioner;
        this.tenant = tenant;
        this.timeout = timeout;
        this.clock = clock;
        this.prepared = prepared;
        this.validate = validate;
        this.dockerImageRepository = session.getDockerImageRepository();
        this.version = session.getVespaVersion();
        this.isBootstrap = isBootstrap;
        this.athenzDomain = session.getAthenzDomain();
    }

    public static Deployment unprepared(LocalSession session, ApplicationRepository applicationRepository,
                                        Optional<Provisioner> hostProvisioner, Tenant tenant,
                                        Duration timeout, Clock clock, boolean validate, boolean isBootstrap) {
        return new Deployment(session, applicationRepository, hostProvisioner, tenant, timeout, clock, false,
                              validate, isBootstrap);
    }

    public static Deployment prepared(LocalSession session, ApplicationRepository applicationRepository,
                                      Optional<Provisioner> hostProvisioner, Tenant tenant,
                                      Duration timeout, Clock clock, boolean isBootstrap) {
        return new Deployment(session, applicationRepository, hostProvisioner, tenant,
                              timeout, clock, true, true, isBootstrap);
    }

    public void setIgnoreSessionStaleFailure(boolean ignoreSessionStaleFailure) {
        this.ignoreSessionStaleFailure = ignoreSessionStaleFailure;
    }

    /** Prepares this. This does nothing if this is already prepared */
    @Override
    public void prepare() {
        if (prepared) return;
        try (ActionTimer timer = applicationRepository.timerFor(session.getApplicationId(), "deployment.prepareMillis")) {
            TimeoutBudget timeoutBudget = new TimeoutBudget(clock, timeout);

            PrepareParams.Builder params = new PrepareParams.Builder().applicationId(session.getApplicationId())
                    .timeoutBudget(timeoutBudget)
                    .ignoreValidationErrors(!validate)
                    .vespaVersion(version.toString())
                    .isBootstrap(isBootstrap);
            dockerImageRepository.ifPresent(params::dockerImageRepository);
            athenzDomain.ifPresent(params::athenzDomain);
            Optional<ApplicationSet> activeApplicationSet = applicationRepository.getCurrentActiveApplicationSet(tenant, session.getApplicationId());
            tenant.getSessionRepository().prepareLocalSession(session, logger, params.build(), activeApplicationSet,
                                                              tenant.getPath(), clock.instant());
            this.prepared = true;
        }
    }

    /** Activates this. If it is not already prepared, this will call prepare first. */
    @Override
    public void activate() {
        if ( ! prepared)
            prepare();

        try (ActionTimer timer = applicationRepository.timerFor(session.getApplicationId(), "deployment.activateMillis")) {
            TimeoutBudget timeoutBudget = new TimeoutBudget(clock, timeout);
            validateSessionStatus(session);
            ApplicationId applicationId = session.getApplicationId();

            if ( ! timeoutBudget.hasTimeLeft()) throw new RuntimeException("Timeout exceeded when trying to activate '" + applicationId + "'");

            RemoteSession previousActiveSession;
            CompletionWaiter waiter;
            try (Lock lock = tenant.getApplicationRepo().lock(applicationId)) {
                previousActiveSession = applicationRepository.getActiveSession(applicationId);
                waiter = applicationRepository.activate(session, previousActiveSession, applicationId, ignoreSessionStaleFailure);
            }
            catch (RuntimeException e) {
                throw e;
            }
            catch (Exception e) {
                throw new InternalServerException("Error activating application", e);
            }

            waiter.awaitCompletion(timeoutBudget.timeLeft());
            log.log(Level.INFO, session.logPre() + "Session " + session.getSessionId() + " activated successfully using " +
                                hostProvisioner.map(provisioner -> provisioner.getClass().getSimpleName()).orElse("no host provisioner") +
                                ". Config generation " + session.getMetaData().getGeneration() +
                                (previousActiveSession != null ? ". Based on session " + previousActiveSession.getSessionId() : "") +
                                ". File references: " + applicationRepository.getFileReferences(applicationId));
        }
    }

    /**
     * Request a restart of services of this application on hosts matching the filter.
     * This is sometimes needed after activation, but can also be requested without
     * doing prepare and activate in the same session.
     */
    @Override
    public void restart(HostFilter filter) {
        hostProvisioner.get().restart(session.getApplicationId(), filter);
    }

    /** Exposes the session of this for testing only */
    public LocalSession session() { return session; }

    private void validateSessionStatus(LocalSession localSession) {
        long sessionId = localSession.getSessionId();
        if (Session.Status.NEW.equals(localSession.getStatus())) {
            throw new IllegalStateException(localSession.logPre() + "Session " + sessionId + " is not prepared");
        } else if (Session.Status.ACTIVATE.equals(localSession.getStatus())) {
            throw new IllegalStateException(localSession.logPre() + "Session " + sessionId + " is already active");
        }
    }

}
