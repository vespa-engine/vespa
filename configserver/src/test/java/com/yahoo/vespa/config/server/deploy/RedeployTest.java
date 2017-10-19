// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.deploy;

import com.google.common.io.Files;
import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.config.model.api.ModelFactory;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ApplicationName;
import com.yahoo.config.provision.InstanceName;
import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.Version;
import com.yahoo.test.ManualClock;
import com.yahoo.vespa.config.server.session.LocalSession;
import com.yahoo.vespa.config.server.session.Session;
import org.junit.Test;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

/**
 * Tests redeploying of an already existing application.
 *
 * @author bratseth
 */
public class RedeployTest {

    @Test
    public void testRedeploy() throws InterruptedException, IOException {
        DeployTester tester = new DeployTester("src/test/apps/app");
        tester.deployApp("myapp", Instant.now());
        Optional<com.yahoo.config.provision.Deployment> deployment = tester.redeployFromLocalActive();

        assertTrue(deployment.isPresent());
        long activeSessionIdBefore = tester.tenant().getLocalSessionRepo().getActiveSession(tester.applicationId()).getSessionId();
        assertEquals(tester.applicationId(), tester.tenant().getLocalSessionRepo().getSession(activeSessionIdBefore).getApplicationId());
        deployment.get().prepare();
        deployment.get().activate();
        long activeSessionIdAfter =  tester.tenant().getLocalSessionRepo().getActiveSession(tester.applicationId()).getSessionId();
        assertEquals(activeSessionIdAfter, activeSessionIdBefore + 1);
        assertEquals(tester.applicationId(), tester.tenant().getLocalSessionRepo().getSession(activeSessionIdAfter).getApplicationId());
    }

    /** No deployment is done because there is no local active session. */
    @Test
    public void testNoRedeploy() {
        List<ModelFactory> modelFactories = new ArrayList<>();
        modelFactories.add(DeployTester.createModelFactory(Clock.systemUTC()));
        modelFactories.add(DeployTester.createFailingModelFactory(Version.fromIntValues(1, 0, 0)));
        DeployTester tester = new DeployTester("ignored/app/path", modelFactories);
        ApplicationId id = ApplicationId.from(TenantName.from("default"),
                                              ApplicationName.from("default"),
                                              InstanceName.from("default"));
        assertFalse(tester.redeployFromLocalActive(id).isPresent());
    }

    @Test
    public void testRedeployWillPurgeOldNonActiveDeployments() {
        ManualClock clock = new ManualClock(Instant.now());
        ConfigserverConfig configserverConfig = new ConfigserverConfig(new ConfigserverConfig.Builder()
                                                                               .configServerDBDir(Files.createTempDir()
                                                                                                       .getAbsolutePath())
                                                                               .configDefinitionsDir(Files.createTempDir()
                                                                                                          .getAbsolutePath())
                                                                               .sessionLifetime(60));
        DeployTester tester = new DeployTester("src/test/apps/app", configserverConfig, clock);
        tester.deployApp("myapp", Instant.now()); // session 2 (numbering starts at 2)

        clock.advance(Duration.ofSeconds(10));
        Optional<com.yahoo.config.provision.Deployment> deployment2 = tester.redeployFromLocalActive();

        assertTrue(deployment2.isPresent());
        deployment2.get().activate(); // session 3
        long activeSessionId = tester.tenant().getLocalSessionRepo().getActiveSession(tester.applicationId()).getSessionId();

        clock.advance(Duration.ofSeconds(10));
        Optional<com.yahoo.config.provision.Deployment> deployment3 = tester.redeployFromLocalActive();
        assertTrue(deployment3.isPresent());
        deployment3.get().prepare();  // session 4 (not activated)

        LocalSession deployment3session = ((Deployment) deployment3.get()).session();
        assertNotEquals(activeSessionId, deployment3session);
        // No change to active session id
        assertEquals(activeSessionId, tester.tenant().getLocalSessionRepo().getActiveSession(tester.applicationId()).getSessionId());
        assertEquals(3, tester.tenant().getLocalSessionRepo().listSessions().size());

        clock.advance(Duration.ofHours(1)); // longer than session lifetime

        // Need another deployment to get old sessions purged
        Optional<com.yahoo.config.provision.Deployment> deployment4 = tester.redeployFromLocalActive();
        assertTrue(deployment4.isPresent());
        deployment4.get().activate();  // session 5

        // Both session 2 (deactivated) and session 4 (never activated) should have been removed
        final Collection<LocalSession> sessions = tester.tenant().getLocalSessionRepo().listSessions();
        assertEquals(2, sessions.size());
        final Set<Long> sessionIds = sessions.stream().map(Session::getSessionId).collect(Collectors.toSet());
        assertTrue(sessionIds.contains(3L));
        assertTrue(sessionIds.contains(5L));
    }

}
