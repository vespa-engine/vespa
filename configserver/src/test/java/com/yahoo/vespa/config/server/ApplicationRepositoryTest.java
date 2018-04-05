// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server;

import com.yahoo.config.model.application.provider.FilesApplicationPackage;
import com.yahoo.config.provision.Provisioner;
import com.yahoo.config.provision.TenantName;
import com.yahoo.vespa.config.server.http.CompressedApplicationInputStreamTest;
import com.yahoo.vespa.config.server.http.SessionHandlerTest;
import com.yahoo.vespa.config.server.http.v2.ApplicationApiHandler;
import com.yahoo.vespa.config.server.http.v2.PrepareResult;
import com.yahoo.vespa.config.server.session.PrepareParams;
import com.yahoo.vespa.config.server.tenant.Tenant;
import com.yahoo.vespa.config.server.tenant.Tenants;
import com.yahoo.vespa.curator.Curator;
import com.yahoo.vespa.curator.mock.MockCurator;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author hmusum
 */
public class ApplicationRepositoryTest {

    private final static File testApp = new File("src/test/apps/app");
    private final static File testAppJdiscOnly = new File("src/test/apps/app-jdisc-only");
    private final static TenantName tenantName = TenantName.from("test");
    private final static Clock clock = Clock.systemUTC();

    private Tenant tenant;
    private ApplicationRepository applicationRepository;
    private TimeoutBudget timeoutBudget;

    @Before
    public void setup() {
        Curator curator = new MockCurator();
        Tenants tenants = new Tenants(new TestComponentRegistry.Builder()
                                              .curator(curator)
                                              .build());
        tenants.addTenant(tenantName);
        tenant = tenants.getTenant(tenantName);
        Provisioner provisioner = new SessionHandlerTest.MockProvisioner();
        applicationRepository = new ApplicationRepository(tenants, provisioner, clock);
        timeoutBudget = new TimeoutBudget(clock, Duration.ofSeconds(60));
    }

    @Test
    public void prepareAndActivate() throws IOException {
        PrepareResult result = prepareAndActivateApp(testApp);
        assertTrue(result.configChangeActions().getRefeedActions().isEmpty());
        assertTrue(result.configChangeActions().getRestartActions().isEmpty());
    }

    @Test
    public void prepareAndActivateWithRestart() throws IOException {
        prepareAndActivateApp(testAppJdiscOnly);
        PrepareResult result = prepareAndActivateApp(testApp);
        assertTrue(result.configChangeActions().getRefeedActions().isEmpty());
        assertFalse(result.configChangeActions().getRestartActions().isEmpty());
    }

    @Test
    public void createAndPrepareAndActivate() throws IOException {
        PrepareResult result = createAndPrepareAndActivateApp();
        assertTrue(result.configChangeActions().getRefeedActions().isEmpty());
        assertTrue(result.configChangeActions().getRestartActions().isEmpty());
    }

    private PrepareResult prepareAndActivateApp(File application) throws IOException {
        FilesApplicationPackage appDir = FilesApplicationPackage.fromFile(application);
        long sessionId = applicationRepository.createSession(tenant, timeoutBudget, appDir.getAppDir(), "testapp");
        return applicationRepository.prepareAndActivate(tenant, sessionId, prepareParams(), false, false, Instant.now());
    }

    private PrepareResult createAndPrepareAndActivateApp() throws IOException {
        File file = CompressedApplicationInputStreamTest.createTarFile();
        return applicationRepository.createSessionAndPrepareAndActivate(tenant, new FileInputStream(file),
                                                                        ApplicationApiHandler.APPLICATION_X_GZIP,
                                                                        timeoutBudget, "testapp", prepareParams(),
                                                                        false, false, Instant.now());
    }

    private PrepareParams prepareParams() {
        return new PrepareParams.Builder().build();
    }

}
