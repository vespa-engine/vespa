// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator;

import ai.vespa.http.DomainName;
import com.yahoo.config.model.api.SuperModel;
import com.yahoo.config.model.api.SuperModelListener;
import com.yahoo.config.model.api.SuperModelProvider;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.Zone;
import com.yahoo.jdisc.Metric;
import com.yahoo.jdisc.test.TestTimer;
import com.yahoo.vespa.applicationmodel.HostName;
import com.yahoo.vespa.applicationmodel.ServiceStatus;
import com.yahoo.vespa.applicationmodel.ServiceStatusInfo;
import com.yahoo.vespa.flags.InMemoryFlagSource;
import com.yahoo.vespa.orchestrator.controller.ClusterControllerClientFactoryMock;
import com.yahoo.vespa.orchestrator.model.ApplicationApiFactory;
import com.yahoo.vespa.orchestrator.policy.HostStateChangeDeniedException;
import com.yahoo.vespa.orchestrator.policy.HostedVespaClusterPolicy;
import com.yahoo.vespa.orchestrator.policy.HostedVespaPolicy;
import com.yahoo.vespa.orchestrator.status.InMemoryStatusService;
import com.yahoo.vespa.service.duper.ConfigServerApplication;
import com.yahoo.vespa.service.duper.DuperModel;
import com.yahoo.vespa.service.duper.DuperModelManager;
import com.yahoo.vespa.service.manager.UnionMonitorManager;
import com.yahoo.vespa.service.model.ServiceMonitorImpl;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author hakon
 */
public class OrchestratorTest {

    private static final Zone zone = Zone.defaultZone();

    private final InMemoryStatusService statusService = new InMemoryStatusService();
    private DuperModelManager duperModelManager;
    private MySuperModelProvider superModelManager;
    private UnionMonitorManager monitorManager;
    private OrchestratorImpl orchestrator;

    @Before
    public void setUp() {
        var flagSource = new InMemoryFlagSource();
        var timer = new TestTimer();
        var clustercontroller = new ClusterControllerClientFactoryMock();
        var applicationApiFactory = new ApplicationApiFactory(3, 5, timer.toUtcClock());
        var clusterPolicy = new HostedVespaClusterPolicy(flagSource, zone);
        var policy = new HostedVespaPolicy(clusterPolicy, clustercontroller, applicationApiFactory, flagSource);
        var zone = new Zone(SystemName.cd, Environment.prod, RegionName.from("cd-us-east-1"));
        this.superModelManager = new MySuperModelProvider();
        var duperModel = new DuperModel();
        this.duperModelManager = new DuperModelManager(true, false, superModelManager, duperModel);
        this.monitorManager = mock(UnionMonitorManager.class);
        var metric = mock(Metric.class);
        var serviceMonitor = new ServiceMonitorImpl(duperModelManager, monitorManager, metric, timer, zone);

        this.orchestrator = new OrchestratorImpl(policy,
                clustercontroller,
                statusService,
                serviceMonitor,
                0,
                timer.toUtcClock(),
                applicationApiFactory,
                flagSource);
    }

    @Test
    public void simulate_config_server_reprovision() throws OrchestrationException {
        // All services are healthy at all times
        when(monitorManager.getStatus(any(), any(), any(), any())).thenReturn(new ServiceStatusInfo(ServiceStatus.UP));

        // There are no ordinary applications
        superModelManager.markAsComplete();

        // There is one config server application with 3 nodes
        ApplicationId applicationId = new ConfigServerApplication().getApplicationId();
        var cfg1 = DomainName.of("cfg1");
        var cfg2 = DomainName.of("cfg2");
        var cfg3 = DomainName.of("cfg3");
        duperModelManager.infraApplicationActivated(applicationId, List.of(cfg1, cfg2, cfg3));
        duperModelManager.infraApplicationsIsNowComplete();

        // cfg1 completes retirement
        orchestrator.acquirePermissionToRemove(toApplicationModelHostName(cfg1));

        // No other cfg is allowed to go permanently down BEFORE cfg1 is removed from the app
        try {
            orchestrator.acquirePermissionToRemove(toApplicationModelHostName(cfg2));
            fail();
        } catch (HostStateChangeDeniedException e) {
            assertTrue(e.getMessage().contains("Changing the state of cfg2 would violate enough-services-up"));
            assertTrue(e.getMessage().contains("[cfg1] are suspended."));
        }

        // cfg1 is removed from the application
        duperModelManager.infraApplicationActivated(applicationId, List.of(cfg2, cfg3));

        // No other cfg is allowed to go permanently down AFTER cfg1 is removed from the app
        try {
            orchestrator.acquirePermissionToRemove(toApplicationModelHostName(cfg2));
            fail();
        } catch (HostStateChangeDeniedException e) {
            assertTrue(e.getMessage().contains("Changing the state of cfg2 would violate enough-services-up"));
            assertTrue(e.getMessage().contains("[1 missing config server] are down."));
        }

        // cfg1 is reprovisioned, added to the node repo, and activated
        duperModelManager.infraApplicationActivated(applicationId, List.of(cfg1, cfg2, cfg3));

        // cfg2 is allowed to be removed
        orchestrator.acquirePermissionToRemove(toApplicationModelHostName(cfg2));

        // No other cfg is allowed to go permanently down BEFORE cfg2 is removed from the app
        try {
            orchestrator.acquirePermissionToRemove(toApplicationModelHostName(cfg1));
            fail();
        } catch (HostStateChangeDeniedException e) {
            assertTrue(e.getMessage().contains("Changing the state of cfg1 would violate enough-services-up"));
            assertTrue(e.getMessage().contains("[cfg2] are suspended"));
        }

        // etc (should be the same as for cfg1)
    }

    private HostName toApplicationModelHostName(DomainName hostname) {
        return new HostName(hostname.value());
    }

    private static class MySuperModelProvider implements SuperModelProvider {
        private boolean complete = false;
        private SuperModelListener listener = null;

        @Override
        public void registerListener(SuperModelListener listener) {
            if (this.listener != null) {
                throw new IllegalStateException("This instance already has a listener");
            }

            this.listener = listener;
        }

        public void markAsComplete() {
            complete = true;

            if (listener == null) {
                throw new IllegalStateException("This instance has no listener");
            }
            listener.notifyOfCompleteness(getSuperModel());
        }

        @Override
        public SuperModel getSuperModel() {
            return new SuperModel(Map.of(), complete);
        }
    }
}
