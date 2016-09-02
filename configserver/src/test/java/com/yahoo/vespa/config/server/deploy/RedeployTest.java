// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.deploy;

import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ApplicationName;
import com.yahoo.config.provision.InstanceName;
import com.yahoo.config.provision.TenantName;
import com.yahoo.path.Path;
import com.yahoo.vespa.config.server.ApplicationRepository;
import com.yahoo.vespa.config.server.tenant.TestWithTenant;
import com.yahoo.vespa.config.server.TimeoutBudget;
import com.yahoo.vespa.config.server.provision.HostProvisionerProvider;
import com.yahoo.vespa.config.server.session.LocalSession;
import com.yahoo.vespa.config.server.session.PrepareParams;
import com.yahoo.vespa.config.server.session.SilentDeployLogger;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Tests redeploying of an already existing application.
 *
 * @author bratseth
 */
public class RedeployTest extends TestWithTenant {

    @Test
    public void testRedeploy() throws InterruptedException, IOException {
        DeployTester tester = new DeployTester("src/test/apps/app");
        tester.deployApp(tenant, "myapp");
        Optional<com.yahoo.config.provision.Deployment> deployment = tester.redeployFromLocalActive(tenants, curator);

        assertTrue(deployment.isPresent());
        long activeSessionIdBefore = tenant.getLocalSessionRepo().getActiveSession(tester.applicationId()).getSessionId();
        assertEquals(tester.applicationId(), tenant.getLocalSessionRepo().getSession(activeSessionIdBefore).getApplicationId());
        deployment.get().prepare();
        deployment.get().activate();
        long activeSessionIdAfter =  tenant.getLocalSessionRepo().getActiveSession(tester.applicationId()).getSessionId();
        assertEquals(activeSessionIdAfter, activeSessionIdBefore + 1);
        assertEquals(tester.applicationId(), tenant.getLocalSessionRepo().getSession(activeSessionIdAfter).getApplicationId());
    }

    /** No deployment is done because there isn't a local active session. */
    @Test
    public void testNoRedeploy() {
        ApplicationId id = ApplicationId.from(TenantName.from("default"),
                                              ApplicationName.from("default"),
                                              InstanceName.from("default"));

        ApplicationRepository applicationRepository = new ApplicationRepository(tenants, HostProvisionerProvider.empty(),
                                                                                new ConfigserverConfig(new ConfigserverConfig.Builder()), curator);

        assertFalse(applicationRepository.deployFromLocalActive(id, Duration.ofSeconds(60)).isPresent());
    }

}
