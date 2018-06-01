// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.session;

import com.google.common.io.Files;
import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.config.application.api.ApplicationFile;
import com.yahoo.config.provision.*;
import com.yahoo.path.Path;
import com.yahoo.config.model.application.provider.*;
import com.yahoo.slime.Slime;
import com.yahoo.vespa.config.server.*;
import com.yahoo.vespa.config.server.application.MemoryTenantApplications;
import com.yahoo.vespa.config.server.deploy.DeployHandlerLogger;
import com.yahoo.vespa.config.server.deploy.TenantFileSystemDirs;
import com.yahoo.vespa.config.server.deploy.ZooKeeperClient;
import com.yahoo.vespa.config.server.host.HostRegistry;
import com.yahoo.vespa.config.server.tenant.TenantRepository;
import com.yahoo.vespa.curator.Curator;
import com.yahoo.vespa.curator.mock.MockCurator;
import com.yahoo.vespa.config.server.zookeeper.ConfigCurator;

import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.time.Instant;
import java.util.*;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.*;

/**
 * @author Ulf Lilleengen
 */
public class LocalSessionTest {

    private Path tenantPath = Path.createRoot();
    private Curator curator;
    private ConfigCurator configCurator;
    private TenantFileSystemDirs tenantFileSystemDirs;
    private SuperModelGenerationCounter superModelGenerationCounter;

    @Before
    public void setupTest() {
        curator = new MockCurator();
        configCurator = ConfigCurator.create(curator);
        superModelGenerationCounter = new SuperModelGenerationCounter(curator);
        tenantFileSystemDirs = new TenantFileSystemDirs(Files.createTempDir(), TenantName.from("test_tenant"));
    }

    @Test
    public void require_that_session_is_initialized() throws Exception {
        LocalSession session = createSession(TenantName.defaultName(), 2);
        assertThat(session.getSessionId(), is(2l));
        session = createSession(TenantName.defaultName(), Long.MAX_VALUE);
        assertThat(session.getSessionId(), is(Long.MAX_VALUE));
        assertThat(session.getActiveSessionAtCreate(), is(0l));
    }

    @Test
    public void require_that_session_status_is_updated() throws Exception {
        LocalSession session = createSession(TenantName.defaultName(), 3);
        assertThat(session.getStatus(), is(Session.Status.NEW));
        doPrepare(session, Instant.now());
        assertThat(session.getStatus(), is(Session.Status.PREPARE));
        session.createActivateTransaction().commit();
        assertThat(session.getStatus(), is(Session.Status.ACTIVATE));
    }

    @Test
    public void require_that_marking_session_modified_changes_status_to_new() throws Exception {
        LocalSession session = createSession(TenantName.defaultName(), 3);
        doPrepare(session, Instant.now());
        assertThat(session.getStatus(), is(Session.Status.PREPARE));
        session.getApplicationFile(Path.createRoot(), LocalSession.Mode.READ);
        assertThat(session.getStatus(), is(Session.Status.PREPARE));
        session.getApplicationFile(Path.createRoot(), LocalSession.Mode.WRITE);
        assertThat(session.getStatus(), is(Session.Status.NEW));
    }

    @Test
    public void require_that_preparer_is_run() throws Exception {
        SessionTest.MockSessionPreparer preparer = new SessionTest.MockSessionPreparer();
        LocalSession session = createSession(TenantName.defaultName(), 3, preparer);
        assertFalse(preparer.isPrepared);
        doPrepare(session, Instant.now());
        assertTrue(preparer.isPrepared);
        assertThat(session.getStatus(), is(Session.Status.PREPARE));
    }

    @Test
    public void require_that_session_status_can_be_deactivated() throws Exception {
        SessionTest.MockSessionPreparer preparer = new SessionTest.MockSessionPreparer();
        LocalSession session = createSession(TenantName.defaultName(), 3, preparer);
        session.createDeactivateTransaction().commit();
        assertThat(session.getStatus(), is(Session.Status.DEACTIVATE));
    }

    private File testApp = new File("src/test/apps/app");

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
        long gen = superModelGenerationCounter.get();
        session.delete();
        assertThat(superModelGenerationCounter.get(), is(gen + 1));
        assertFalse(configCurator.exists(sessionNode));
        assertFalse(new File(tenantFileSystemDirs.sessionsPath(), "3").exists());
    }

    @Test(expected = IllegalStateException.class)
    public void require_that_no_provision_info_throws_exception() throws Exception {
        createSession(TenantName.defaultName(), 3).getAllocatedHosts();
    }

    @Test
    public void require_that_provision_info_can_be_read() throws Exception {
        AllocatedHosts input = AllocatedHosts.withHosts(Collections.singleton(new HostSpec("myhost", Collections.<String>emptyList())));

        LocalSession session = createSession(TenantName.defaultName(), 3, new SessionTest.MockSessionPreparer(), Optional.of(input));
        ApplicationId origId = new ApplicationId.Builder()
                               .tenant("tenant")
                               .applicationName("foo").instanceName("quux").build();
        doPrepare(session, new PrepareParams.Builder().applicationId(origId).build(), Instant.now());
        AllocatedHosts info = session.getAllocatedHosts();
        assertNotNull(info);
        assertThat(info.getHosts().size(), is(1));
        assertTrue(info.getHosts().contains(new HostSpec("myhost", Collections.emptyList())));
    }

    @Test
    public void require_that_application_metadata_is_correct() throws Exception {
        LocalSession session = createSession(TenantName.defaultName(), 3);
        doPrepare(session, new PrepareParams.Builder().build(), Instant.now());
        assertThat(session.getMetaData().toString(), is("n/a, n/a, 0, 0, , 0"));
    }

    private LocalSession createSession(TenantName tenant, long sessionId) throws Exception {
        SessionTest.MockSessionPreparer preparer = new SessionTest.MockSessionPreparer();
        return createSession(tenant, sessionId, preparer);
    }

    private LocalSession createSession(TenantName tenant, long sessionId, SessionTest.MockSessionPreparer preparer) throws Exception {
        return createSession(tenant, sessionId, preparer, Optional.<AllocatedHosts>empty());
    }

    private LocalSession createSession(TenantName tenant, long sessionId, SessionTest.MockSessionPreparer preparer, Optional<AllocatedHosts> allocatedHosts) throws Exception {
        SessionZooKeeperClient zkc = new MockSessionZKClient(curator, tenant, sessionId, allocatedHosts);
        zkc.createWriteStatusTransaction(Session.Status.NEW).commit();
        ZooKeeperClient zkClient = new ZooKeeperClient(configCurator, new BaseDeployLogger(), false, TenantRepository.getSessionsPath(tenant).append(String.valueOf(sessionId)));
        if (allocatedHosts.isPresent()) {
            zkClient.write(allocatedHosts.get());
        }
        zkClient.write(Collections.singletonMap(Version.fromIntValues(0, 0, 0), new MockFileRegistry()));
        File sessionDir = new File(tenantFileSystemDirs.sessionsPath(), String.valueOf(sessionId));
        sessionDir.createNewFile();
        return new LocalSession(tenant, sessionId, preparer, new SessionContext(FilesApplicationPackage.fromFile(testApp), zkc, sessionDir, new MemoryTenantApplications(), new HostRegistry<>(), superModelGenerationCounter));
    }

    private void doPrepare(LocalSession session, Instant now) {
        doPrepare(session, new PrepareParams.Builder().build(), now);
    }

    private void doPrepare(LocalSession session, PrepareParams params, Instant now) {
        session.prepare(getLogger(false), params, Optional.empty(), tenantPath, now);
    }

    DeployHandlerLogger getLogger(boolean verbose) {
        return new DeployHandlerLogger(new Slime().get(), verbose,
                                       new ApplicationId.Builder().tenant("testtenant").applicationName("testapp").build());
    }
}
