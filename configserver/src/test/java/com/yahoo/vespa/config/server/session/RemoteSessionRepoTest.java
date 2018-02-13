// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.session;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.TenantName;
import com.yahoo.path.Path;
import com.yahoo.text.Utf8;
import com.yahoo.transaction.Transaction;

import com.yahoo.vespa.config.server.TestComponentRegistry;
import com.yahoo.vespa.config.server.TestWithCurator;
import com.yahoo.vespa.config.server.application.TenantApplications;
import com.yahoo.vespa.config.server.tenant.Tenant;
import com.yahoo.vespa.config.server.tenant.TenantBuilder;
import com.yahoo.vespa.config.server.tenant.Tenants;
import com.yahoo.vespa.curator.Curator;
import org.junit.Before;
import org.junit.Test;

import com.yahoo.vespa.config.server.zookeeper.ConfigCurator;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.LongPredicate;

/**
 * @author Ulf Lilleengen
 * @since 5.1
 */
public class RemoteSessionRepoTest extends TestWithCurator {

    private static final TenantName tenantName = TenantName.defaultName();

    private RemoteSessionRepo remoteSessionRepo;

    @Before
    public void setupFacade() throws Exception {
        Tenant tenant = TenantBuilder.create(new TestComponentRegistry.Builder()
                                                     .curator(curator)
                                                     .build(),
                                             tenantName)
                .build();
        this.remoteSessionRepo = tenant.getRemoteSessionRepo();
        curator.create(Tenants.getTenantPath(tenantName).append("/applications"));
        curator.create(Tenants.getSessionsPath(tenantName));
        createSession(1l, false);
        createSession(2l, false);
    }

    private void createSession(long sessionId, boolean wait) {
        createSession(sessionId, wait, tenantName);
    }

    private void createSession(long sessionId, boolean wait, TenantName tenantName) {
        Path sessionsPath = Tenants.getSessionsPath(tenantName);
        SessionZooKeeperClient zkc = new SessionZooKeeperClient(curator, sessionsPath.append(String.valueOf(sessionId)));
        zkc.createNewSession(System.currentTimeMillis(), TimeUnit.MILLISECONDS);
        if (wait) {
            Curator.CompletionWaiter waiter = zkc.getUploadWaiter();
            waiter.awaitCompletion(Duration.ofSeconds(120));
        }
    }

    @Test
    public void testInitialize() {
        assertSessionExists(1l);
        assertSessionExists(2l);
    }

    @Test
    public void testCreateSession() throws Exception {
        createSession(3l, true);
        assertSessionExists(3l);
    }

    @Test
    public void testSessionStateChange() throws Exception {
        long sessionId = 3L;
        createSession(sessionId, true);
        assertSessionStatus(sessionId, Session.Status.NEW);
        assertStatusChange(sessionId, Session.Status.PREPARE);
        assertStatusChange(sessionId, Session.Status.ACTIVATE);

        Path session = Tenants.getSessionsPath(tenantName).append("" + sessionId);
        curator.delete(session);
        assertSessionRemoved(sessionId);
        assertNull(remoteSessionRepo.getSession(sessionId));
    }

    // If reading a session throws an exception it should be handled and not prevent other applications
    // from loading. In this test we just show that we end up with one session in remote session
    // repo even if it had bad data (by making getSessionIdForApplication() in FailingTenantApplications
    // throw an exception).
    @Test
    public void testBadApplicationRepoOnActivate() throws Exception {
        long sessionId = 3L;
        TenantApplications applicationRepo = new FailingTenantApplications();
        TenantName mytenant = TenantName.from("mytenant");
        Tenant tenant = TenantBuilder.create(new TestComponentRegistry.Builder().curator(curator).build(), mytenant)
                .withApplicationRepo(applicationRepo)
                .build();
        curator.create(Tenants.getSessionsPath(mytenant));
        remoteSessionRepo = tenant.getRemoteSessionRepo();
        assertThat(remoteSessionRepo.listSessions().size(), is(0));
        createSession(sessionId, true, mytenant);
        assertThat(remoteSessionRepo.listSessions().size(), is(1));
    }

    private void assertStatusChange(long sessionId, Session.Status status) throws Exception {
        Path statePath = Tenants.getSessionsPath(tenantName).append("" + sessionId).append(ConfigCurator.SESSIONSTATE_ZK_SUBPATH);
        curator.create(statePath);
        curatorFramework.setData().forPath(statePath.getAbsolute(), Utf8.toBytes(status.toString()));
        System.out.println("Setting status " + status + " for " + sessionId);
        assertSessionStatus(sessionId, status);
    }

    private void assertSessionRemoved(long sessionId) {
        waitFor(p -> remoteSessionRepo.getSession(sessionId) == null, sessionId);
        assertNull(remoteSessionRepo.getSession(sessionId));
    }

    private void assertSessionExists(long sessionId) {
        assertSessionStatus(sessionId, Session.Status.NEW);
    }

    private void assertSessionStatus(long sessionId, Session.Status status) {
        waitFor(p -> remoteSessionRepo.getSession(sessionId) != null &&
                remoteSessionRepo.getSession(sessionId).getStatus() == status, sessionId);
        assertNotNull(remoteSessionRepo.getSession(sessionId));
        assertThat(remoteSessionRepo.getSession(sessionId).getStatus(), is(status));
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

    private class FailingTenantApplications implements TenantApplications {

        @Override
        public List<ApplicationId> listApplications() {
            return Collections.singletonList(ApplicationId.defaultId());
        }

        @Override
        public Transaction createPutApplicationTransaction(ApplicationId applicationId, long sessionId) {
            return null;
        }

        @Override
        public long getSessionIdForApplication(ApplicationId applicationId) {
            throw new IllegalArgumentException("Bad id " + applicationId);
        }

        @Override
        public Transaction deleteApplication(ApplicationId applicationId) {
            return null;
        }

        @Override
        public void removeUnusedApplications() {
            // do nothing
        }

        @Override
        public void close() {

        }
    }
}
