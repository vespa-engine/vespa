// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.deploy;

import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ApplicationName;
import com.yahoo.config.provision.InstanceName;
import com.yahoo.config.provision.TenantName;
import com.yahoo.path.Path;
import com.yahoo.vespa.config.server.TestWithTenant;
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

    private static final Path appPath = Path.createRoot().append("testapp");
    private File testApp = new File("src/test/apps/app");
    private Path tenantPath = appPath;

    @Test
    public void testRedeploy() throws InterruptedException, IOException {
        ApplicationId id = deployApp();

        Deployer deployer = new Deployer(tenants, HostProvisionerProvider.empty(),
                                         new ConfigserverConfig(new ConfigserverConfig.Builder()), curator);

        Optional<com.yahoo.config.provision.Deployment> deployment = deployer.deployFromLocalActive(id, Duration.ofSeconds(60));
        assertTrue(deployment.isPresent());
        long activeSessionIdBefore = tenant.getLocalSessionRepo().getActiveSession(id).getSessionId();
        assertEquals(id, tenant.getLocalSessionRepo().getSession(activeSessionIdBefore).getApplicationId());
        deployment.get().prepare();
        deployment.get().activate();
        long activeSessionIdAfter =  tenant.getLocalSessionRepo().getActiveSession(id).getSessionId();
        assertEquals(activeSessionIdAfter, activeSessionIdBefore + 1);
        assertEquals(id, tenant.getLocalSessionRepo().getSession(activeSessionIdAfter).getApplicationId());
    }

    /** No deploYMENT is done because there isn't a local active session. */
    @Test
    public void testNoRedeploy() {
        ApplicationId id = ApplicationId.from(TenantName.from("default"),
                                              ApplicationName.from("default"),
                                              InstanceName.from("default"));

        Deployer deployer = new Deployer(tenants, HostProvisionerProvider.empty(),
                                         new ConfigserverConfig(new ConfigserverConfig.Builder()), curator);

        assertFalse(deployer.deployFromLocalActive(id, Duration.ofSeconds(60)).isPresent());
    }

    /**
     * Do the initial "deploy" with the existing API-less code as the deploy API doesn't support first deploys yet.
     */
    private ApplicationId deployApp() throws InterruptedException, IOException {
        LocalSession session = tenant.getSessionFactory().createSession(testApp, "default", new SilentDeployLogger(), new TimeoutBudget(Clock.systemUTC(), Duration.ofSeconds(60)));
        ApplicationId id = ApplicationId.from(tenant.getName(), ApplicationName.from("myapp"), InstanceName.defaultName());
        session.prepare(new SilentDeployLogger(), new PrepareParams(new ConfigserverConfig(new ConfigserverConfig.Builder())).applicationId(id), Optional.empty(), tenantPath);
        session.createActivateTransaction().commit();
        tenant.getLocalSessionRepo().addSession(session);
        return id;
    }

}
