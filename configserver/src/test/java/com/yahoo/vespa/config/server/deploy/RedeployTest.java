// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.deploy;

import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.model.NullConfigModelRegistry;
import com.yahoo.config.model.api.Model;
import com.yahoo.config.model.api.ModelContext;
import com.yahoo.config.model.api.ModelCreateResult;
import com.yahoo.config.model.api.ModelFactory;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.test.MockApplicationPackage;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ApplicationName;
import com.yahoo.config.provision.InstanceName;
import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.Version;
import com.yahoo.path.Path;
import com.yahoo.test.ManualClock;
import com.yahoo.vespa.config.server.ApplicationRepository;
import com.yahoo.vespa.config.server.TestComponentRegistry;
import com.yahoo.vespa.config.server.TestWithCurator;
import com.yahoo.vespa.config.server.modelfactory.ModelFactoryRegistry;
import com.yahoo.vespa.config.server.monitoring.Metrics;
import com.yahoo.vespa.config.server.tenant.Tenant;
import com.yahoo.vespa.config.server.tenant.Tenants;
import com.yahoo.vespa.config.server.tenant.TestWithTenant;
import com.yahoo.vespa.config.server.TimeoutBudget;
import com.yahoo.vespa.config.server.provision.HostProvisionerProvider;
import com.yahoo.vespa.config.server.session.LocalSession;
import com.yahoo.vespa.config.server.session.PrepareParams;
import com.yahoo.vespa.config.server.session.SilentDeployLogger;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.VespaModelFactory;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Tests redeploying of an already existing application.
 *
 * @author bratseth
 */
public class RedeployTest {

    @Test
    public void testRedeploy() throws InterruptedException, IOException {
        DeployTester tester = new DeployTester("src/test/apps/app");
        tester.deployApp("myapp");
        Optional<com.yahoo.config.provision.Deployment> deployment = tester.redeployFromLocalActive();

        assertTrue(deployment.isPresent());
        long activeSessionIdBefore = tester.tenant().getLocalSessionRepo().getActiveSession(tester.applicationId()).getSessionId();
        assertEquals(tester.applicationId(), tester.tenant().getLocalSessionRepo().getSession(activeSessionIdBefore).getApplicationId());
        deployment.get().prepare();
        deployment.get().activate();
        long activeSessionIdAfter =  tester.tenant().getLocalSessionRepo().getActiveSession(tester.applicationId()).getSessionId();
        assertEquals(activeSessionIdAfter, activeSessionIdBefore + 1);
        assertEquals(tester.applicationId(), tester.tenant().getLocalSessionRepo().getSession(activeSessionIdAfter).getApplicationId());
    }

    /** No deployment is done because there is no local active session. */
    @Test
    public void testNoRedeploy() {
        List<ModelFactory> modelFactories = new ArrayList<>();
        modelFactories.add(DeployTester.createDefaultModelFactory(Clock.systemUTC()));
        modelFactories.add(DeployTester.createFailingModelFactory(Version.fromIntValues(1, 0, 0)));
        DeployTester tester = new DeployTester("ignored/app/path", modelFactories);
        ApplicationId id = ApplicationId.from(TenantName.from("default"),
                                              ApplicationName.from("default"),
                                              InstanceName.from("default"));
        assertFalse(tester.redeployFromLocalActive(id).isPresent());
    }
    
}
