// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.session;

import com.google.common.io.Files;
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
import com.yahoo.transaction.NestedTransaction;
import com.yahoo.vespa.config.server.MockReloadHandler;
import com.yahoo.vespa.config.server.TestComponentRegistry;
import com.yahoo.vespa.config.server.application.TenantApplications;
import com.yahoo.vespa.config.server.deploy.DeployHandlerLogger;
import com.yahoo.vespa.config.server.deploy.TenantFileSystemDirs;
import com.yahoo.vespa.config.server.deploy.ZooKeeperClient;
import com.yahoo.vespa.config.server.host.HostRegistry;
import com.yahoo.vespa.config.server.tenant.TenantRepository;
import com.yahoo.vespa.config.server.zookeeper.ConfigCurator;
import com.yahoo.vespa.curator.Curator;
import com.yahoo.vespa.curator.mock.MockCurator;
import com.yahoo.vespa.flags.InMemoryFlagSource;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
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

    private final InMemoryFlagSource flagSource = new InMemoryFlagSource();
    private Path tenantPath = Path.createRoot();
    private Curator curator;
    private ConfigCurator configCurator;
    private TenantFileSystemDirs tenantFileSystemDirs;

    @Before
    public void setupTest() {
        curator = new MockCurator();
        configCurator = ConfigCurator.create(curator);
        tenantFileSystemDirs = new TenantFileSystemDirs(Files.createTempDir(), TenantName.from("test_tenant"));
    }

    @Test
    public void require_that_session_is_initialized() throws Exception {
        LocalSession session = createSession(TenantName.defaultName(), 2);
        assertThat(session.getSessionId(), is(2L));
        session = createSession(TenantName.defaultName(), Long.MAX_VALUE);
        assertThat(session.getSessionId(), is(Long.MAX_VALUE));
        assertThat(session.getActiveSessionAtCreate(), is(0L));
    }

    @Test
    public void require_that_session_status_is_updated() throws Exception {
        LocalSession session = createSession(TenantName.defaultName(), 3);
        assertThat(session.getStatus(), is(Session.Status.NEW));
        doPrepare(session);
        assertThat(session.getStatus(), is(Session.Status.PREPARE));
        session.createActivateTransaction().commit();
        assertThat(session.getStatus(), is(Session.Status.ACTIVATE));
    }

    @Test
    public void require_that_marking_session_modified_changes_status_to_new() throws Exception {
        LocalSession session = createSession(TenantName.defaultName(), 3);
        doPrepare(session);
        assertThat(session.getStatus(), is(Session.Status.PREPARE));
        session.getApplicationFile(Path.createRoot(), LocalSession.Mode.READ);
        assertThat(session.getStatus(), is(Session.Status.PREPARE));
        session.getApplicationFile(Path.createRoot(), LocalSession.Mode.WRITE);
        assertThat(session.getStatus(), is(Session.Status.NEW));
    }

    @Test
    public void require_that_application_file_can_be_fetched() throws Exception {
        LocalSession session = createSession(TenantName.defaultName(), 3);
        ApplicationFile f1 = session.getApplicationFile(Path.fromString("services.xml"), LocalSession.Mode.READ);
        ApplicationFile f2 = session.getApplicationFile(Path.fromString("services2.xml"), LocalSession.Mode.READ);
        assertTrue(f1.exists());
        assertFalse(f2.exists());
    }

    @Test
    public void require_that_session_can_be_deleted() throws Exception {
        TenantName tenantName = TenantName.defaultName();
        LocalSession session = createSession(tenantName, 3);
        String sessionNode = TenantRepository.getSessionsPath(tenantName).append(String.valueOf(3)).getAbsolute();
        assertTrue(configCurator.exists(sessionNode));
        assertTrue(new File(tenantFileSystemDirs.sessionsPath(), "3").exists());
        NestedTransaction transaction = new NestedTransaction();
        session.delete(transaction);
        transaction.commit();
        assertFalse(configCurator.exists(sessionNode));
        assertFalse(new File(tenantFileSystemDirs.sessionsPath(), "3").exists());
    }

    @Test(expected = IllegalStateException.class)
    public void require_that_no_provision_info_throws_exception() throws Exception {
        createSession(TenantName.defaultName(), 3).getAllocatedHosts();
    }

    private LocalSession createSession(TenantName tenant, long sessionId) throws Exception {
        SessionTest.MockSessionPreparer preparer = new SessionTest.MockSessionPreparer();
        return createSession(tenant, sessionId, preparer);
    }

    private LocalSession createSession(TenantName tenant, long sessionId, SessionTest.MockSessionPreparer preparer) throws Exception {
        return createSession(tenant, sessionId, preparer, Optional.empty());
    }

    private LocalSession createSession(TenantName tenant, long sessionId, SessionTest.MockSessionPreparer preparer, Optional<AllocatedHosts> allocatedHosts) throws Exception {
        SessionZooKeeperClient zkc = new MockSessionZKClient(curator, tenant, sessionId, allocatedHosts);
        zkc.createWriteStatusTransaction(Session.Status.NEW).commit();
        ZooKeeperClient zkClient = new ZooKeeperClient(configCurator, new BaseDeployLogger(), false, TenantRepository.getSessionsPath(tenant).append(String.valueOf(sessionId)));
        if (allocatedHosts.isPresent()) {
            zkClient.write(allocatedHosts.get());
        }
        zkClient.write(Collections.singletonMap(new Version(0, 0, 0), new MockFileRegistry()));
        File sessionDir = new File(tenantFileSystemDirs.sessionsPath(), String.valueOf(sessionId));
        sessionDir.createNewFile();
        TenantApplications applications = TenantApplications.create(new TestComponentRegistry.Builder().curator(curator).build(), new MockReloadHandler(), tenant);
        applications.createApplication(zkc.readApplicationId());
        return new LocalSession(tenant, sessionId, preparer,
                new SessionContext(
                        FilesApplicationPackage.fromFile(testApp),
                        zkc,
                        sessionDir,
                        applications,
                        new HostRegistry<>(),
                        flagSource));
    }

    private void doPrepare(LocalSession session) {
        doPrepare(session, new PrepareParams.Builder().build());
    }

    private void doPrepare(LocalSession session, PrepareParams params) {
        session.prepare(getLogger(), params, Optional.empty(), tenantPath, Instant.now());
    }

    private DeployHandlerLogger getLogger() {
        return new DeployHandlerLogger(new Slime().get(), false,
                                       new ApplicationId.Builder().tenant("testtenant").applicationName("testapp").build());
    }

}
