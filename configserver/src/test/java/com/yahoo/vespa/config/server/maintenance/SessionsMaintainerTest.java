// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.maintenance;

import com.yahoo.config.model.application.provider.FilesApplicationPackage;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Deployment;
import com.yahoo.config.provision.TenantName;
import com.yahoo.test.ManualClock;
import com.yahoo.vespa.config.server.ApplicationRepository;
import com.yahoo.vespa.config.server.deploy.TenantFileSystemDirs;
import com.yahoo.vespa.config.server.session.LocalSession;
import com.yahoo.vespa.config.server.session.PrepareParams;
import com.yahoo.vespa.config.server.session.SessionRepository;
import com.yahoo.vespa.config.server.session.SessionZooKeeperClient;
import com.yahoo.vespa.flags.FlagSource;
import com.yahoo.vespa.flags.InMemoryFlagSource;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import static com.yahoo.vespa.config.server.session.Session.Status.PREPARE;
import static com.yahoo.vespa.config.server.session.Session.Status.UNKNOWN;
import static com.yahoo.vespa.flags.PermanentFlags.CONFIG_SERVER_SESSION_EXPIRY_TIME;
import static com.yahoo.yolean.Exceptions.uncheck;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SessionsMaintainerTest {

    private static final File testApp = new File("src/test/apps/hosted");
    private static final ApplicationId applicationId = ApplicationId.from("deploytester", "myApp", "default");
    private static final long sessionLifeTime = 60;

    private final ManualClock clock = new ManualClock();
    private final InMemoryFlagSource flagSource = new InMemoryFlagSource();
    private MaintainerTester tester;
    private ApplicationRepository applicationRepository;
    private SessionsMaintainer maintainer;
    private SessionRepository sessionRepository;
    private TenantFileSystemDirs tenantFileSystemDirs;

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void testDeletion() {
        tester = createTester();
        tester.deployApp(testApp, prepareParams()); // session 2 (numbering starts at 2)

        clock.advance(Duration.ofSeconds(10));
        createDeployment().activate(); // session 3
        long activeSessionId = getActiveSessionId(applicationRepository);

        // Deploy, but do not activate
        clock.advance(Duration.ofSeconds(10));
        var deployment = createDeployment();
        deployment.prepare(); // session 4 (not activated)

        var deployment3session = ((com.yahoo.vespa.config.server.deploy.Deployment) deployment).session();
        assertNotEquals(activeSessionId, deployment3session.getSessionId());
        // No change to active session id
        assertEquals(activeSessionId, getActiveSessionId(applicationRepository));
        assertNumberOfLocalSessions(3);
        assertNumberOfRemoteSessions(3);

        // advance clock more than session lifetime
        clock.advance(Duration.ofSeconds(applicationRepository.configserverConfig().sessionLifetime() + 10));

        // All sessions except session id 3 should be removed after maintainer has run
        maintainer.run();
        var localSessions = new ArrayList<>(sessionRepository.getLocalSessions());
        assertEquals(1, localSessions.size());
        var localSession = localSessions.get(0);
        assertEquals(3, localSession.getSessionId());

        var remoteSessions = new ArrayList<>(sessionRepository.getRemoteSessionsFromZooKeeper());
        assertEquals(1, remoteSessions.size());
        var remoteSession = sessionRepository.getRemoteSession(remoteSessions.get(0));
        assertEquals(3, remoteSession.getSessionId());
    }

    @Test
    public void testDeletionOfSessionWithNoData() throws IOException {
        tester = createTester();
        tester.deployApp(testApp, prepareParams()); // session 2 (numbering starts at 2)

        // Deploy, but do not activate
        createDeployment().prepare(); // session 3 (not activated)
        assertNumberOfLocalSessions(2);
        assertNumberOfRemoteSessions(2);

        // Create a local session without any data in zookeeper (corner case seen in production occasionally)
        int sessionId = 4;
        var tenantName = applicationId.tenant();
        Files.createDirectory(tenantFileSystemDirs.getUserApplicationDir(sessionId).toPath());
        createLocalSession(tenantName, sessionId);
        assertNumberOfLocalSessions(3);
        assertNumberOfRemoteSessions(2);

        // and check that expiring local sessions still works
        clock.advance(Duration.ofHours(1));
        maintainer.run();
        assertNumberOfLocalSessions(2);
        assertNumberOfRemoteSessions(1); // Only session 2 left
    }

    @Test
    public void testDeletionOfSessionWithUnknownStatus() {
        tester = createTester();
        tester.deployApp(testApp, prepareParams()); // session 2 (numbering starts at 2)

        // Deploy, but do not activate
        createDeployment().prepare(); // session 3 (not activated)
        assertNumberOfLocalSessions(2);
        assertNumberOfRemoteSessions(2);

        // Create a session, set status to UNKNOWN, we don't want to expire those
        // (creation time is then EPOCH, so will be candidate for expiry)
        var sessionId = 5;
        var session = sessionRepository.createRemoteSession(sessionId);
        sessionRepository.createSessionZooKeeperClient(sessionId).createNewSession(clock.instant());
        try (var t = sessionRepository.createSetStatusTransaction(session, UNKNOWN)) {
            t.commit();
        }
        assertNumberOfRemoteSessions(3);
        assertNumberOfLocalSessions(2); // Still 2, no new local session

        // Check that trying to expire local session when there exists a local session without any data in zookeeper
        // should not delete session with unknown status
        maintainer.run();
        assertNumberOfRemoteSessions(3);
        assertNumberOfLocalSessions(2);

        // ... but it should be deleted when some time has passed
        clock.advance(Duration.ofHours(keepSessionsWithUnknownStatusHours()).plus(Duration.ofMinutes(1)));
        maintainer.run();
        assertNumberOfRemoteSessions(1); // Only session 2 left
        assertNumberOfLocalSessions(1);
    }

    @Test
    public void testDeletingInactiveSessions3() throws IOException {
        tester = createTester();
        tester.deployApp(testApp, prepareParams()); // session 2 (numbering starts at 2)

        clock.advance(Duration.ofMinutes(10));
        createDeployment().activate(); // session 3
        assertNumberOfLocalSessions(2);

        clock.advance(Duration.ofMinutes(60));
        maintainer.run();
        assertNumberOfLocalSessions(1);

        // Create a local session with invalid application package and check that expiring local sessions still works
        long sessionId = 4;
        var applicationPath = tenantFileSystemDirs.getUserApplicationDir(sessionId).toPath();
        var session = sessionRepository.createRemoteSession(sessionId);
        sessionRepository.createSessionZooKeeperClient(sessionId).createNewSession(clock.instant());
        try (var t = sessionRepository.createSetStatusTransaction(session, PREPARE)) {
            t.commit();
        }
        Files.createDirectory(applicationPath);
        Files.writeString(Files.createFile(applicationPath.resolve("services.xml")), "non-legal xml");
        assertTrue(applicationPath.toFile().exists()); // App has been deleted
        maintainer.run();
        assertNumberOfLocalSessions(1); // Will not show up in local sessions

        // Advance time, session SHOULD be deleted
        clock.advance(Duration.ofHours(keepSessionsWithUnknownStatusHours()).plus(Duration.ofMinutes(1)));
        maintainer.run();
        assertNumberOfLocalSessions(1); // same as before, will not show up in local sessions
        assertFalse(applicationPath.toFile().exists()); // App has been deleted
    }

    private MaintainerTester createTester() {
        return createTester(flagSource);
    }

    private MaintainerTester createTester(FlagSource flagSource) {
        var tester = uncheck(() -> new MaintainerTester(clock, temporaryFolder, flagSource));
        return setup(tester);
    }

    private MaintainerTester setup(MaintainerTester tester) {
        flagSource.withLongFlag(CONFIG_SERVER_SESSION_EXPIRY_TIME.id(), sessionLifeTime);

        applicationRepository = tester.applicationRepository();
        applicationRepository.tenantRepository().addTenant(applicationId.tenant());
        maintainer = new SessionsMaintainer(applicationRepository, tester.curator(), Duration.ofMinutes(1));
        sessionRepository = applicationRepository.getTenant(applicationId).getSessionRepository();

        var serverdb = new File(applicationRepository.configserverConfig().configServerDBDir());
        tenantFileSystemDirs = new TenantFileSystemDirs(serverdb, applicationId.tenant());
        return tester;
    }

    private void createLocalSession(TenantName tenantName, int sessionId) {
        var sessionZooKeeperClient =
                new SessionZooKeeperClient(applicationRepository.tenantRepository().getCurator(), tenantName,
                                           sessionId, applicationRepository.configserverConfig());
        var localSession = new LocalSession(tenantName,
                                            sessionId,
                                            FilesApplicationPackage.fromFile(testApp),
                                            sessionZooKeeperClient);
        sessionRepository.addLocalSession(localSession);
    }

    private long keepSessionsWithUnknownStatusHours() {
        return applicationRepository.configserverConfig().keepSessionsWithUnknownStatusHours();
    }

    private long getActiveSessionId(ApplicationRepository applicationRepository) {
        return applicationRepository.getActiveSession(applicationId).get().getSessionId();
    }

    private void assertNumberOfLocalSessions(int expectedNumberOfSessions) {
        assertEquals(expectedNumberOfSessions, sessionRepository.getLocalSessions().size());
    }

    private void assertNumberOfRemoteSessions(int expectedNumberOfSessions) {
        assertEquals(expectedNumberOfSessions, sessionRepository.getRemoteSessionsFromZooKeeper().size());
    }

    private void assertRemoteSessions(Set<Long> sessions) {
        var set = new HashSet<>(sessionRepository.getRemoteSessionsFromZooKeeper());
        assertEquals(sessions, set);
    }

    private void assertLocalSessions(Set<Long> sessions) {
        var set = new HashSet<>(sessionRepository.getLocalSessionsIdsFromFileSystem());
        assertEquals(sessions, set);
    }

    private PrepareParams.Builder prepareParams() {
        String endpoints = """
                [
                  {
                    "clusterId": "container",
                    "names": [
                      "c.example.com"
                    ],
                    "scope": "zone",
                    "routingMethod": "exclusive"
                  }
                ]
                """;
        return new PrepareParams.Builder().containerEndpoints(endpoints).applicationId(applicationId);
    }

    private Deployment createDeployment() {
        return applicationRepository.deployFromLocalActive(applicationId).get();
    }

}
