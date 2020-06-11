// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.session;

import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.config.model.application.provider.FilesApplicationPackage;
import com.yahoo.config.provision.TenantName;
import com.yahoo.io.IOUtils;
import com.yahoo.text.Utf8;
import com.yahoo.vespa.config.server.GlobalComponentRegistry;
import com.yahoo.vespa.config.server.TestComponentRegistry;
import com.yahoo.vespa.config.server.application.TenantApplications;
import com.yahoo.vespa.config.server.http.SessionHandlerTest;
import com.yahoo.vespa.config.server.tenant.Tenant;
import com.yahoo.vespa.config.server.tenant.TenantRepository;
import com.yahoo.vespa.config.server.zookeeper.ConfigCurator;
import com.yahoo.vespa.curator.Curator;
import com.yahoo.vespa.curator.mock.MockCurator;
import com.yahoo.vespa.flags.InMemoryFlagSource;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.function.LongPredicate;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

/**
 * @author Ulf Lilleengen
 */
public class SessionRepositoryTest {

    private File testApp = new File("src/test/apps/app");
    private MockCurator curator;
    private SessionRepository sessionRepository;
    private TenantRepository tenantRepository;
    private static final TenantName tenantName = TenantName.defaultName();

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Before
    public void setupSessions() throws Exception {
        setupSessions(tenantName, true);
    }

    private void setupSessions(TenantName tenantName, boolean createInitialSessions) throws Exception {
        File configserverDbDir = temporaryFolder.newFolder().getAbsoluteFile();
        if (createInitialSessions) {
            Path sessionsPath = Paths.get(configserverDbDir.getAbsolutePath(), "tenants", tenantName.value(), "sessions");
            IOUtils.copyDirectory(testApp, sessionsPath.resolve("1").toFile());
            IOUtils.copyDirectory(testApp, sessionsPath.resolve("2").toFile());
            IOUtils.copyDirectory(testApp, sessionsPath.resolve("3").toFile());
        }
        curator = new MockCurator();
        curator.create(TenantRepository.getTenantPath(tenantName).append("/applications"));
        curator.create(TenantRepository.getSessionsPath(tenantName));
        GlobalComponentRegistry globalComponentRegistry = new TestComponentRegistry.Builder()
                .curator(curator)
                .configServerConfig(new ConfigserverConfig.Builder()
                                            .configServerDBDir(configserverDbDir.getAbsolutePath())
                                            .configDefinitionsDir(temporaryFolder.newFolder().getAbsolutePath())
                                            .sessionLifetime(5)
                                            .build())
                .build();
        tenantRepository = new TenantRepository(globalComponentRegistry, false);
        TenantApplications applicationRepo = TenantApplications.create(globalComponentRegistry, tenantName);
        sessionRepository = new SessionRepository(tenantName, globalComponentRegistry,
                                                  applicationRepo, applicationRepo, new InMemoryFlagSource(),
                                                  applicationRepo, globalComponentRegistry.getSessionPreparer());
    }

    @Test
    public void require_that_sessions_can_be_loaded_from_disk() {
        assertNotNull(sessionRepository.getSession(1L));
        assertNotNull(sessionRepository.getSession(2L));
        assertNotNull(sessionRepository.getSession(3L));
        assertNull(sessionRepository.getSession(4L));
    }

    @Test
    public void require_that_all_sessions_are_deleted() {
        sessionRepository.close();
        assertNull(sessionRepository.getSession(1L));
        assertNull(sessionRepository.getSession(2L));
        assertNull(sessionRepository.getSession(3L));
    }

    @Test
    public void require_that_sessions_belong_to_a_tenant() {
        // tenant is "default"
        assertNotNull(sessionRepository.getSession(1L));
        assertNotNull(sessionRepository.getSession(2L));
        assertNotNull(sessionRepository.getSession(3L));
        assertNull(sessionRepository.getSession(4L));

        // tenant is "newTenant"
        try {
            setupSessions(TenantName.from("newTenant"), false);
        } catch (Exception e) {
            fail();
        }
        assertNull(sessionRepository.getSession(1L));

        sessionRepository.addSession(new SessionHandlerTest.MockLocalSession(1L, FilesApplicationPackage.fromFile(testApp)));
        sessionRepository.addSession(new SessionHandlerTest.MockLocalSession(2L, FilesApplicationPackage.fromFile(testApp)));
        assertNotNull(sessionRepository.getSession(1L));
        assertNotNull(sessionRepository.getSession(2L));
        assertNull(sessionRepository.getSession(3L));
    }

    private void createSession(long sessionId, boolean wait) {
        createSession(sessionId, wait, tenantName);
    }

    private void createSession(long sessionId, boolean wait, TenantName tenantName) {
        com.yahoo.path.Path sessionsPath = TenantRepository.getSessionsPath(tenantName);
        SessionZooKeeperClient zkc = new SessionZooKeeperClient(curator, sessionsPath.append(String.valueOf(sessionId)));
        zkc.createNewSession(Instant.now());
        if (wait) {
            Curator.CompletionWaiter waiter = zkc.getUploadWaiter();
            waiter.awaitCompletion(Duration.ofSeconds(120));
        }
    }

    @Test
    public void testInitialize() {
        createSession(10L, false);
        createSession(11L, false);
        assertSessionExists(10L);
        assertSessionExists(11L);
    }

    @Test
    public void testCreateSession() {
        createSession(12L, true);
        assertSessionExists(12L);
    }

    @Test
    public void testSessionStateChange() throws Exception {
        long sessionId = 3L;
        createSession(sessionId, true);
        assertSessionStatus(sessionId, Session.Status.NEW);
        assertStatusChange(sessionId, Session.Status.PREPARE);
        assertStatusChange(sessionId, Session.Status.ACTIVATE);

        com.yahoo.path.Path session = TenantRepository.getSessionsPath(tenantName).append("" + sessionId);
        curator.delete(session);
        assertSessionRemoved(sessionId);
        assertNull(sessionRepository.getRemoteSession(sessionId));
    }

    // If reading a session throws an exception it should be handled and not prevent other applications
    // from loading. In this test we just show that we end up with one session in remote session
    // repo even if it had bad data (by making getSessionIdForApplication() in FailingTenantApplications
    // throw an exception).
    @Test
    public void testBadApplicationRepoOnActivate() {
        long sessionId = 3L;
        TenantName mytenant = TenantName.from("mytenant");
        curator.set(TenantRepository.getApplicationsPath(mytenant).append("mytenant:appX:default"), new byte[0]); // Invalid data
        tenantRepository.addTenant(mytenant);
        Tenant tenant = tenantRepository.getTenant(mytenant);
        curator.create(TenantRepository.getSessionsPath(mytenant));
        sessionRepository = tenant.getSessionRepo();
        assertThat(sessionRepository.getRemoteSessions().size(), is(0));
        createSession(sessionId, true, mytenant);
        assertThat(sessionRepository.getRemoteSessions().size(), is(1));
    }

    private void assertStatusChange(long sessionId, Session.Status status) throws Exception {
        com.yahoo.path.Path statePath = TenantRepository.getSessionsPath(tenantName).append("" + sessionId).append(ConfigCurator.SESSIONSTATE_ZK_SUBPATH);
        curator.create(statePath);
        curator.framework().setData().forPath(statePath.getAbsolute(), Utf8.toBytes(status.toString()));
        assertSessionStatus(sessionId, status);
    }

    private void assertSessionRemoved(long sessionId) {
        waitFor(p -> sessionRepository.getRemoteSession(sessionId) == null, sessionId);
        assertNull(sessionRepository.getRemoteSession(sessionId));
    }

    private void assertSessionExists(long sessionId) {
        assertSessionStatus(sessionId, Session.Status.NEW);
    }

    private void assertSessionStatus(long sessionId, Session.Status status) {
        waitFor(p -> sessionRepository.getRemoteSession(sessionId) != null &&
                     sessionRepository.getRemoteSession(sessionId).getStatus() == status, sessionId);
        assertNotNull(sessionRepository.getRemoteSession(sessionId));
        assertThat(sessionRepository.getRemoteSession(sessionId).getStatus(), is(status));
    }

    private void waitFor(LongPredicate predicate, long sessionId) {
        long endTime = System.currentTimeMillis() + 60_000;
        boolean ok;
        do {
            ok = predicate.test(sessionId);
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        } while (System.currentTimeMillis() < endTime && !ok);
    }

}
