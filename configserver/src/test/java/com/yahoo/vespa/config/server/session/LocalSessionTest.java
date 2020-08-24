// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.session;

import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.component.Version;
import com.yahoo.config.application.api.ApplicationFile;
import com.yahoo.config.model.application.provider.BaseDeployLogger;
import com.yahoo.config.model.application.provider.FilesApplicationPackage;
import com.yahoo.config.model.application.provider.MockFileRegistry;
import com.yahoo.config.provision.AllocatedHosts;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.TenantName;
import com.yahoo.path.Path;
import com.yahoo.slime.Slime;
import com.yahoo.vespa.config.server.TestComponentRegistry;
import com.yahoo.vespa.config.server.application.TenantApplications;
import com.yahoo.vespa.config.server.deploy.DeployHandlerLogger;
import com.yahoo.vespa.config.server.deploy.ZooKeeperClient;
import com.yahoo.vespa.config.server.tenant.TenantRepository;
import com.yahoo.vespa.config.server.zookeeper.ConfigCurator;
import com.yahoo.vespa.curator.Curator;
import com.yahoo.vespa.curator.mock.MockCurator;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.Collections;
import java.util.Optional;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

/**
 * @author Ulf Lilleengen
 */
public class LocalSessionTest {

    private static final File testApp = new File("src/test/apps/app");
    private static final TenantName tenantName = TenantName.from("test_tenant");
    private static final Path tenantPath = Path.createRoot();

    private TenantRepository tenantRepository;
    private Curator curator;
    private ConfigCurator configCurator;

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Before
    public void setupTest() throws IOException {
        curator = new MockCurator();
        TestComponentRegistry componentRegistry = new TestComponentRegistry.Builder()
                .curator(curator)
                .configServerConfig(new ConfigserverConfig.Builder()
                                            .configDefinitionsDir(temporaryFolder.newFolder().getAbsolutePath())
                                            .configServerDBDir(temporaryFolder.newFolder().getAbsolutePath())
                                            .build())
                .build();
        tenantRepository = new TenantRepository(componentRegistry);
        tenantRepository.addTenant(tenantName);
        configCurator = ConfigCurator.create(curator);
    }

    @Test
    public void require_that_session_is_initialized() throws Exception {
        LocalSession session = createSession(applicationId(), 2);
        assertThat(session.getSessionId(), is(2L));
        session = createSession(applicationId(), Long.MAX_VALUE);
        assertThat(session.getSessionId(), is(Long.MAX_VALUE));
        assertThat(session.getActiveSessionAtCreate(), is(0L));
    }

    @Test
    public void require_that_marking_session_modified_changes_status_to_new() throws Exception {
        LocalSession session = createSession(applicationId(), 3);
        doPrepare(session, applicationId());
        assertThat(session.getStatus(), is(Session.Status.PREPARE));
        session.getApplicationFile(Path.createRoot(), Session.Mode.READ);
        assertThat(session.getStatus(), is(Session.Status.PREPARE));
        session.getApplicationFile(Path.createRoot(), Session.Mode.WRITE);
        assertThat(session.getStatus(), is(Session.Status.NEW));
    }

    @Test
    public void require_that_application_file_can_be_fetched() throws Exception {
        LocalSession session = createSession(applicationId(), 3);
        ApplicationFile f1 = session.getApplicationFile(Path.fromString("services.xml"), Session.Mode.READ);
        ApplicationFile f2 = session.getApplicationFile(Path.fromString("services2.xml"), Session.Mode.READ);
        assertTrue(f1.exists());
        assertFalse(f2.exists());
    }

    @Test(expected = IllegalStateException.class)
    public void require_that_no_provision_info_throws_exception() throws Exception {
        createSession(applicationId(), 3).getAllocatedHosts();
    }

    private LocalSession createSession(ApplicationId applicationId, long sessionId) throws Exception {
        return createSession(applicationId, sessionId, Optional.empty());
    }

    private LocalSession createSession(ApplicationId applicationId, long sessionId,
                                       Optional<AllocatedHosts> allocatedHosts) throws Exception {
        TenantName tenantName = applicationId.tenant();
        SessionZooKeeperClient zkc = new MockSessionZKClient(curator, tenantName, sessionId, allocatedHosts);
        zkc.createWriteStatusTransaction(Session.Status.NEW).commit();
        ZooKeeperClient zkClient = new ZooKeeperClient(configCurator, new BaseDeployLogger(),
                                                       TenantRepository.getSessionsPath(tenantName).append(String.valueOf(sessionId)));
        if (allocatedHosts.isPresent()) {
            zkClient.write(allocatedHosts.get());
        }
        zkClient.write(Collections.singletonMap(new Version(0, 0, 0), new MockFileRegistry()));
        TenantApplications applications = tenantRepository.getTenant(tenantName).getApplicationRepo();
        applications.createApplication(applicationId);
        LocalSession session = new LocalSession(tenantName, sessionId, FilesApplicationPackage.fromFile(testApp), zkc);
        session.setApplicationId(applicationId);
        return session;
    }

    private void doPrepare(LocalSession session, ApplicationId applicationId) {
        doPrepare(session, new PrepareParams.Builder().applicationId(applicationId).build());
    }

    private void doPrepare(LocalSession session, PrepareParams params) {
        SessionRepository sessionRepository = tenantRepository.getTenant(params.getApplicationId().tenant()).getSessionRepository();
        sessionRepository.prepareLocalSession(session, getLogger(), params, Optional.empty(), tenantPath, Instant.now());
    }

    private DeployHandlerLogger getLogger() {
        return new DeployHandlerLogger(new Slime().get(), false, applicationId());
    }

    private ApplicationId applicationId() {
        return new ApplicationId.Builder().tenant(tenantName).applicationName("testapp").build();
    }

}
