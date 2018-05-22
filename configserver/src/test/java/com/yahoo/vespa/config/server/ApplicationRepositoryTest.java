// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server;

import com.yahoo.component.Version;
import com.yahoo.component.Vtag;
import com.yahoo.config.model.application.provider.FilesApplicationPackage;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ApplicationName;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.InstanceName;
import com.yahoo.config.provision.Provisioner;
import com.yahoo.config.provision.TenantName;
import com.yahoo.vespa.config.server.http.CompressedApplicationInputStream;
import com.yahoo.vespa.config.server.http.CompressedApplicationInputStreamTest;
import com.yahoo.vespa.config.server.http.SessionHandlerTest;
import com.yahoo.vespa.config.server.http.v2.ApplicationApiHandler;
import com.yahoo.vespa.config.server.http.v2.PrepareResult;
import com.yahoo.vespa.config.server.session.PrepareParams;
import com.yahoo.vespa.config.server.tenant.Tenant;
import com.yahoo.vespa.config.server.tenant.TenantRepository;
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author hmusum
 */
public class ApplicationRepositoryTest {

    private final static File testApp = new File("src/test/apps/app");
    private final static File testAppJdiscOnly = new File("src/test/apps/app-jdisc-only");
    private final static File testAppJdiscOnlyRestart = new File("src/test/apps/app-jdisc-only-restart");

    private final static TenantName tenantName = TenantName.from("test");
    private final static Clock clock = Clock.systemUTC();

    private Tenant tenant;
    private ApplicationRepository applicationRepository;
    private TimeoutBudget timeoutBudget;

    @Before
    public void setup() {
        Curator curator = new MockCurator();
        TenantRepository tenants = new TenantRepository(new TestComponentRegistry.Builder()
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
        PrepareResult result = prepareAndActivateApp(testAppJdiscOnlyRestart);
        assertTrue(result.configChangeActions().getRefeedActions().isEmpty());
        assertFalse(result.configChangeActions().getRestartActions().isEmpty());
    }

    @Test
    public void createAndPrepareAndActivate() throws IOException {
        PrepareResult result = deployApp();
        assertTrue(result.configChangeActions().getRefeedActions().isEmpty());
        assertTrue(result.configChangeActions().getRestartActions().isEmpty());
    }

    @Test
    public void deleteUnusedTenants() throws IOException {
        deployApp();
        assertTrue(applicationRepository.removeUnusedTenants().isEmpty());
        applicationRepository.remove(applicationId());
        assertEquals(tenantName, applicationRepository.removeUnusedTenants().iterator().next());
    }

    @Test
    public void decideVersion() {
        ApplicationId regularApp = ApplicationId.from("tenant1", "application1", "default");
        ApplicationId systemApp = ApplicationId.from("hosted-vespa", "routing", "default");
        Version targetVersion = Version.fromString("5.0");

        // Always use target for system application
        assertEquals(targetVersion, ApplicationRepository.decideVersion(systemApp, Environment.prod, targetVersion));
        assertEquals(targetVersion, ApplicationRepository.decideVersion(systemApp, Environment.dev, targetVersion));
        assertEquals(targetVersion, ApplicationRepository.decideVersion(systemApp, Environment.perf, targetVersion));

        // Target for regular application depends on environment
        assertEquals(targetVersion, ApplicationRepository.decideVersion(regularApp, Environment.prod, targetVersion));
        assertEquals(Vtag.currentVersion, ApplicationRepository.decideVersion(regularApp, Environment.dev, targetVersion));
        assertEquals(Vtag.currentVersion, ApplicationRepository.decideVersion(regularApp, Environment.perf, targetVersion));
    }

    private PrepareResult prepareAndActivateApp(File application) throws IOException {
        FilesApplicationPackage appDir = FilesApplicationPackage.fromFile(application);
        long sessionId = applicationRepository.createSession(applicationId(), timeoutBudget, appDir.getAppDir());
        return applicationRepository.prepareAndActivate(tenant, sessionId, prepareParams(), false, false, Instant.now());
    }

    private PrepareResult deployApp() throws IOException {
        File file = CompressedApplicationInputStreamTest.createTarFile();
        return applicationRepository.deploy(CompressedApplicationInputStream.createFromCompressedStream(
                                                    new FileInputStream(file), ApplicationApiHandler.APPLICATION_X_GZIP),
                                            prepareParams(), false, false, Instant.now());
    }

    private PrepareParams prepareParams() {
        return new PrepareParams.Builder().applicationId(applicationId()).build();
    }

    private ApplicationId applicationId() {
        return ApplicationId.from(tenantName, ApplicationName.from("testapp"), InstanceName.defaultName());
    }

}
