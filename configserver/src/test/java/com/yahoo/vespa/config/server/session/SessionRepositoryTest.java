// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.session;

import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.TenantName;
import com.yahoo.text.Utf8;
import com.yahoo.vespa.config.server.ApplicationRepository;
import com.yahoo.vespa.config.server.GlobalComponentRegistry;
import com.yahoo.vespa.config.server.TestComponentRegistry;
import com.yahoo.vespa.config.server.application.OrchestratorMock;
import com.yahoo.vespa.config.server.http.SessionHandlerTest;
import com.yahoo.vespa.config.server.tenant.Tenant;
import com.yahoo.vespa.config.server.tenant.TenantRepository;
import com.yahoo.vespa.config.server.zookeeper.ConfigCurator;
import com.yahoo.vespa.curator.Curator;
import com.yahoo.vespa.curator.mock.MockCurator;
import com.yahoo.vespa.flags.FlagSource;
import com.yahoo.vespa.flags.InMemoryFlagSource;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.function.LongPredicate;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;

/**
 * @author Ulf Lilleengen
 */
public class SessionRepositoryTest {

    private static final TenantName tenantName = TenantName.defaultName();
    private static final ApplicationId applicationId = ApplicationId.from(tenantName.value(), "testApp", "default");
    private static final File testApp = new File("src/test/apps/app");

    private MockCurator curator;
    private TenantRepository tenantRepository;
    private ApplicationRepository applicationRepository;

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    public void setup() throws Exception {
       setup(new InMemoryFlagSource());
    }

    private void setup(FlagSource flagSource) throws Exception {
        curator = new MockCurator();
        File configserverDbDir = temporaryFolder.newFolder().getAbsoluteFile();
        GlobalComponentRegistry globalComponentRegistry = new TestComponentRegistry.Builder()
                .curator(curator)
                .configServerConfig(new ConfigserverConfig.Builder()
                                            .configServerDBDir(configserverDbDir.getAbsolutePath())
                                            .configDefinitionsDir(temporaryFolder.newFolder().getAbsolutePath())
                                            .fileReferencesDir(temporaryFolder.newFolder().getAbsolutePath())
                                            .sessionLifetime(5)
                                            .build())
                .flagSource(flagSource)
                .build();
        tenantRepository = new TenantRepository(globalComponentRegistry, false);
        tenantRepository.addTenant(SessionRepositoryTest.tenantName);
        applicationRepository = new ApplicationRepository(tenantRepository,
                                                          new SessionHandlerTest.MockProvisioner(),
                                                          new OrchestratorMock(),
                                                          Clock.systemUTC());
    }

    @Test
    public void require_that_local_sessions_are_created_and_deleted() throws Exception {
        setup();
        long firstSessionId = deploy();
        long secondSessionId = deploy();
        SessionRepository sessionRepository = tenantRepository.getTenant(tenantName).getSessionRepository();
        assertNotNull(sessionRepository.getLocalSession(firstSessionId));
        assertNotNull(sessionRepository.getLocalSession(secondSessionId));
        assertNull(sessionRepository.getLocalSession(secondSessionId + 1));

        sessionRepository.close();
        // All created sessions are deleted
        assertNull(sessionRepository.getLocalSession(firstSessionId));
        assertNull(sessionRepository.getLocalSession(secondSessionId));
    }

    @Test
    public void require_that_local_sessions_belong_to_a_tenant() throws Exception {
        setup();
        // tenant is "default"

        long firstSessionId = deploy();
        long secondSessionId = deploy();
        SessionRepository sessionRepository = tenantRepository.getTenant(tenantName).getSessionRepository();
        assertNotNull(sessionRepository.getLocalSession(firstSessionId));
        assertNotNull(sessionRepository.getLocalSession(secondSessionId));
        assertNull(sessionRepository.getLocalSession(secondSessionId + 1));

        // tenant is "newTenant"
        TenantName newTenant = TenantName.from("newTenant");
        tenantRepository.addTenant(newTenant);
        long sessionId = deploy(ApplicationId.from(newTenant.value(), "testapp", "default"));
        SessionRepository sessionRepository2 = tenantRepository.getTenant(newTenant).getSessionRepository();
        assertNotNull(sessionRepository2.getLocalSession(sessionId));
    }

    @Test
    public void testInitialize() throws Exception {
        setup();
        createSession(10L, false);
        createSession(11L, false);
        assertRemoteSessionExists(10L);
        assertRemoteSessionExists(11L);
    }

    @Test
    public void testSessionStateChange() throws Exception {
        setup();
        long sessionId = 3L;
        createSession(sessionId, true);
        assertRemoteSessionStatus(sessionId, Session.Status.NEW);
        assertStatusChange(sessionId, Session.Status.PREPARE);
        assertStatusChange(sessionId, Session.Status.ACTIVATE);

        com.yahoo.path.Path session = TenantRepository.getSessionsPath(tenantName).append("" + sessionId);
        curator.delete(session);
        assertSessionRemoved(sessionId);
        SessionRepository sessionRepository = tenantRepository.getTenant(tenantName).getSessionRepository();
        assertNull(sessionRepository.getRemoteSession(sessionId));
    }

    // If reading a session throws an exception it should be handled and not prevent other applications
    // from loading. In this test we just show that we end up with one session in remote session
    // repo even if it had bad data (by making getSessionIdForApplication() in FailingTenantApplications
    // throw an exception).
    @Test
    public void testBadApplicationRepoOnActivate() throws Exception {
        setup();
        long sessionId = 3L;
        TenantName mytenant = TenantName.from("mytenant");
        curator.set(TenantRepository.getApplicationsPath(mytenant).append("mytenant:appX:default"), new byte[0]); // Invalid data
        tenantRepository.addTenant(mytenant);
        Tenant tenant = tenantRepository.getTenant(mytenant);
        curator.create(TenantRepository.getSessionsPath(mytenant));
        SessionRepository sessionRepository = tenant.getSessionRepo();
        assertThat(sessionRepository.getRemoteSessions().size(), is(0));
        createSession(sessionId, true, mytenant);
        assertThat(sessionRepository.getRemoteSessions().size(), is(1));
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

    private void assertStatusChange(long sessionId, Session.Status status) throws Exception {
        com.yahoo.path.Path statePath = TenantRepository.getSessionsPath(tenantName).append("" + sessionId).append(ConfigCurator.SESSIONSTATE_ZK_SUBPATH);
        curator.create(statePath);
        curator.framework().setData().forPath(statePath.getAbsolute(), Utf8.toBytes(status.toString()));
        assertRemoteSessionStatus(sessionId, status);
    }

    private void assertSessionRemoved(long sessionId) {
        SessionRepository sessionRepository = tenantRepository.getTenant(tenantName).getSessionRepository();
        waitFor(p -> sessionRepository.getRemoteSession(sessionId) == null, sessionId);
        assertNull(sessionRepository.getRemoteSession(sessionId));
    }

    private void assertRemoteSessionExists(long sessionId) {
        assertRemoteSessionStatus(sessionId, Session.Status.NEW);
    }

    private void assertRemoteSessionStatus(long sessionId, Session.Status status) {
        SessionRepository sessionRepository = tenantRepository.getTenant(tenantName).getSessionRepository();
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

    private long deploy() {
        return deploy(applicationId);
    }

    private long deploy(ApplicationId applicationId) {
        applicationRepository.deploy(testApp, new PrepareParams.Builder().applicationId(applicationId).build());
        return applicationRepository.getActiveSession(applicationId).getSessionId();
    }

}
