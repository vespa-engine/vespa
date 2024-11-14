// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.deploy;

import ai.vespa.metrics.ConfigServerMetrics;
import com.yahoo.config.FileReference;
import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.config.provision.ActivationContext;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ApplicationLockException;
import com.yahoo.config.provision.ApplicationMutex;
import com.yahoo.config.provision.ApplicationTransaction;
import com.yahoo.config.provision.HostFilter;
import com.yahoo.config.provision.HostSpec;
import com.yahoo.config.provision.Provisioner;
import com.yahoo.config.provision.TransientException;
import com.yahoo.transaction.NestedTransaction;
import com.yahoo.vespa.config.server.ApplicationRepository;
import com.yahoo.vespa.config.server.ApplicationRepository.ActionTimer;
import com.yahoo.vespa.config.server.ApplicationRepository.Activation;
import com.yahoo.vespa.config.server.TimeoutBudget;
import com.yahoo.vespa.config.server.configchange.ConfigChangeActions;
import com.yahoo.vespa.config.server.configchange.RestartActions;
import com.yahoo.vespa.config.server.session.ActivationTriggers.NodeRestart;
import com.yahoo.vespa.config.server.session.ActivationTriggers.Reindexing;
import com.yahoo.vespa.config.server.session.PrepareParams;
import com.yahoo.vespa.config.server.session.Session;
import com.yahoo.vespa.config.server.session.SessionRepository;
import com.yahoo.vespa.config.server.tenant.Tenant;
import com.yahoo.yolean.Exceptions;
import com.yahoo.yolean.concurrent.Memoized;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.yahoo.vespa.config.server.session.Session.Status.DELETE;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toSet;

/**
 * The process of deploying an application.
 * Deployments are created by an {@link ApplicationRepository}.
 * Instances of this are not multi-thread safe.
 *
 * @author Ulf Lilleengen
 * @author bratseth
 */
public class Deployment implements com.yahoo.config.provision.Deployment {

    private static final Logger log = Logger.getLogger(Deployment.class.getName());
    private static final Duration durationBetweenResourceReadyChecks = Duration.ofSeconds(60);

    /** The session containing the application instance to activate */
    private final Session session;
    private final ApplicationRepository applicationRepository;
    private final Supplier<PrepareParams> params;
    private final Optional<Provisioner> provisioner;
    private final Tenant tenant;
    private final DeployLogger deployLogger;
    private final Clock clock;

    private boolean prepared;
    private ConfigChangeActions configChangeActions;

    private Deployment(Session session, ApplicationRepository applicationRepository, Supplier<PrepareParams> params,
                       Optional<Provisioner> provisioner, Tenant tenant, DeployLogger deployLogger, Clock clock, boolean prepared) {
        this.session = session;
        this.applicationRepository = applicationRepository;
        this.params = params;
        this.provisioner = provisioner;
        this.tenant = tenant;
        this.deployLogger = deployLogger;
        this.clock = clock;
        this.prepared = prepared;
    }

    public static Deployment unprepared(Session session, ApplicationRepository applicationRepository,
                                        Optional<Provisioner> provisioner, Tenant tenant, PrepareParams params, DeployLogger logger, Clock clock) {
        return new Deployment(session, applicationRepository, () -> params, provisioner, tenant, logger, clock, false);
    }

    public static Deployment unprepared(Session session, ApplicationRepository applicationRepository,
                                        Optional<Provisioner> provisioner, Tenant tenant, DeployLogger logger,
                                        Duration timeout, Clock clock, boolean validate, boolean isBootstrap) {
        Supplier<PrepareParams> params = createPrepareParams(clock, timeout, session, true, isBootstrap, !validate, false, true);
        return new Deployment(session, applicationRepository, params, provisioner, tenant, logger, clock, false);
    }

    public static Deployment prepared(Session session, ApplicationRepository applicationRepository,
                                      Optional<Provisioner> provisioner, Tenant tenant, DeployLogger logger,
                                      Duration timeout, Clock clock, boolean isBootstrap, boolean force) {
        Supplier<PrepareParams> params = createPrepareParams(clock, timeout, session, false, isBootstrap, false, force, false);
        return new Deployment(session, applicationRepository, params, provisioner, tenant, logger, clock, true);
    }

    /** Prepares this. This does nothing if this is already prepared */
    @Override
    public void prepare() {
        if (prepared) return;

        try (ActionTimer timer = applicationRepository.timerFor(params.get().getApplicationId(), ConfigServerMetrics.DEPLOYMENT_PREPARE_MILLIS.baseName())) {
            this.configChangeActions = sessionRepository().prepareLocalSession(session, deployLogger, params.get(), clock.instant());
            this.prepared = true;
        } catch (Exception e) {
            log.log(Level.FINE, "Preparing session " + session.getSessionId() + " failed, deleting it");
            deleteSession();
            throw e;
        }
    }

    /** Activates this. If it is not already prepared, this will call prepare first. */
    @Override
    public long activate() {
        prepare();

        validateSessionStatus(session);

        waitForResourcesOrTimeout(params.get(), session, provisioner);

        ApplicationId applicationId = session.getApplicationId();
        try (ActionTimer timer = applicationRepository.timerFor(applicationId, ConfigServerMetrics.DEPLOYMENT_ACTIVATE_MILLIS.baseName())) {
            TimeoutBudget timeoutBudget = params.get().getTimeoutBudget();
            timeoutBudget.assertNotTimedOut(() -> "Timeout exceeded when trying to activate '" + applicationId + "'");

            Activation activation = applicationRepository.activate(session, applicationId, tenant, params.get().force());
            waitForActivation(applicationId, timeoutBudget, activation);
            restartServicesIfNeeded(applicationId);
            storeReindexing(applicationId);

            return session.getMetaData().getGeneration();
        }
    }

    private void waitForActivation(ApplicationId applicationId, TimeoutBudget timeoutBudget, Activation activation) {
        activation.awaitCompletion(timeoutBudget.timeLeft());
        Set<FileReference> fileReferences = applicationRepository.getFileReferences(applicationId);
        String fileReferencesText = fileReferences.size() > 10
                ? " " + fileReferences.size() + " file references"
                : "File references: " + fileReferences;
        log.log(Level.INFO, session.logPre() + "Session " + session.getSessionId() + " activated successfully using " +
                provisioner.map(provisioner -> provisioner.getClass().getSimpleName()).orElse("no host provisioner") +
                ". Config generation " + session.getMetaData().getGeneration() +
                activation.sourceSessionId().stream().mapToObj(id -> ". Based on session " + id).findFirst().orElse("") +
                ". " + fileReferencesText);
    }

    private void deleteSession() {
        sessionRepository().deleteLocalSession(session.getSessionId());
        try (var transaction = sessionRepository().createSetStatusTransaction(session, DELETE)) {
            transaction.commit();
        }
    }

    private SessionRepository sessionRepository() {
        return tenant.getSessionRepository();
    }

    private void restartServicesIfNeeded(ApplicationId applicationId) {
        if (provisioner.isEmpty()) return;

        Set<String> nodesToRestart = session.getActivationTriggers().nodeRestarts().stream().map(NodeRestart::hostname).collect(toSet());
        if (nodesToRestart.isEmpty()) return;

        applicationRepository.modifyPendingRestarts(applicationId, pendingRestarts -> pendingRestarts.withRestarts(session.getSessionId(), nodesToRestart));
        deployLogger.log(Level.INFO, String.format("Scheduled service restart of %d nodes: %s",
                                                   nodesToRestart.size(), nodesToRestart.stream().sorted().collect(joining(", "))));
        log.info(String.format("%sWill schedule service restart of %d nodes after convergence on generation %d: %s",
                               session.logPre(), nodesToRestart.size(), session.getSessionId(), nodesToRestart.stream().sorted().collect(joining(", "))));
        configChangeActions = configChangeActions == null ? null : configChangeActions.withRestartActions(new RestartActions());
    }

    private void storeReindexing(ApplicationId applicationId) {
        if ( ! applicationRepository.configserverConfig().hostedVespa()) return;

        List<Reindexing> entries = session.getActivationTriggers().reindexings();
        if (entries.isEmpty()) return;

        applicationRepository.modifyReindexing(applicationId, reindexing -> {
            for (Reindexing entry : entries)
                reindexing = reindexing.withPending(entry.clusterId(), entry.documentType(), session.getSessionId());

            return reindexing;
        });
        deployLogger.log(Level.INFO, String.format("Scheduled reindexing of %d document types across %d clusters: %s",
                                                   entries.size(),
                                                   entries.stream().map(Reindexing::clusterId).distinct().count(),
                                                   entries.stream().collect(groupingBy(Reindexing::clusterId)).entrySet().stream()
                                                          .map(typesInCluster -> typesInCluster.getKey() + ": " +
                                                                                 typesInCluster.getValue().stream()
                                                                                               .map(Reindexing::documentType).collect(joining(", ")))
                                                          .collect(joining("; "))));
    }

    /**
     * Request a restart of services of this application on hosts matching the filter.
     * This is sometimes needed after activation, but can also be requested without
     * doing prepare and activate in the same session.
     */
    @Override
    public void restart(HostFilter filter) {
        provisioner.get().restart(session.getApplicationId(), filter);
    }

    /** Exposes the session of this for testing only */
    public Session session() { return session; }

    /**
     * @return config change actions that need to be performed as result of prepare
     * @throws IllegalArgumentException if called without being prepared by this
     */
    public ConfigChangeActions configChangeActions() {
        if (configChangeActions != null) return configChangeActions;
        throw new IllegalArgumentException("No config change actions: " + (prepared ? "was already prepared" : "not yet prepared"));
    }

    private void validateSessionStatus(Session session) {
        long sessionId = session.getSessionId();
        if (Session.Status.NEW.equals(session.getStatus())) {
            throw new IllegalArgumentException(session.logPre() + "Session " + sessionId + " is not prepared");
        } else if (Session.Status.ACTIVATE.equals(session.getStatus())) {
            throw new IllegalArgumentException(session.logPre() + "Session " + sessionId + " is already active");
        }
    }

    /**
     * @param clock system clock
     * @param timeout total timeout duration of prepare + activate
     * @param session the local session for this deployment
     * @param isBootstrap true if this deployment is done to bootstrap the config server
     * @param ignoreValidationErrors whether this model should be validated
     * @param force whether activation of this model should be forced
     */
    private static Supplier<PrepareParams> createPrepareParams(
            Clock clock, Duration timeout, Session session, boolean isInternalRedeployment,
            boolean isBootstrap, boolean ignoreValidationErrors, boolean force, boolean waitForResourcesInPrepare) {

        // Use supplier because we shouldn't/can't create this before validateSessionStatus() for prepared deployments,
        // memoize because we want to create this once for unprepared deployments
        return new Memoized<>(() -> {
            TimeoutBudget timeoutBudget = new TimeoutBudget(clock, timeout);

            PrepareParams.Builder params = new PrepareParams.Builder()
                    .applicationId(session.getApplicationId())
                    .vespaVersion(session.getVespaVersion().toString())
                    .timeoutBudget(timeoutBudget)
                    .ignoreValidationErrors(ignoreValidationErrors)
                    .isBootstrap(isBootstrap)
                    .isInternalRedeployment(isInternalRedeployment)
                    .force(force)
                    .waitForResourcesInPrepare(waitForResourcesInPrepare)
                    .tenantSecretStores(session.getTenantSecretStores())
                    .dataplaneTokens(session.getDataplaneTokens());
            session.getDockerImageRepository().ifPresent(params::dockerImageRepository);
            session.getAthenzDomain().ifPresent(params::athenzDomain);
            session.getCloudAccount().ifPresent(params::cloudAccount);

            return params.build();
        });
    }

    private static void waitForResourcesOrTimeout(PrepareParams params, Session session, Optional<Provisioner> provisioner) {
        if (!params.waitForResourcesInPrepare() || provisioner.isEmpty()) return;

        Set<HostSpec> preparedHosts = session.getAllocatedHosts().getHosts();
        ActivationContext context = new ActivationContext(session.getSessionId());
        AtomicReference<Exception> lastException = new AtomicReference<>();

        while (true) {
            params.getTimeoutBudget().assertNotTimedOut(
                    () -> "Timeout exceeded while waiting for application resources of '" + session.getApplicationId() + "'" +
                          Optional.ofNullable(lastException.get()).map(e -> ". Last exception: " + Exceptions.toMessageString(e)).orElse(""),
                    lastException.get());
            try (ApplicationMutex lock = provisioner.get().lock(session.getApplicationId())) {
                // Call to activate to make sure that everything is ready, but do not commit the transaction
                ApplicationTransaction transaction = new ApplicationTransaction(lock, new NestedTransaction());
                provisioner.get().activate(preparedHosts, context, transaction);
                return;
            } catch (ApplicationLockException | TransientException e) {
                lastException.set(e);
                try {
                    Thread.sleep(durationBetweenResourceReadyChecks.toMillis());
                } catch (InterruptedException e1) {
                    throw new RuntimeException(e1);
                }
            }
        }
    }

}
