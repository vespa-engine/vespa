// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.session;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.TenantName;
import com.yahoo.path.Path;
import com.yahoo.text.Utf8;
import com.yahoo.transaction.Transaction;
import com.yahoo.vespa.config.server.*;

import com.yahoo.vespa.config.server.application.TenantApplications;
import com.yahoo.vespa.config.server.tenant.Tenant;
import com.yahoo.vespa.config.server.tenant.TenantBuilder;
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
 * @author lulf
 * @since 5.1
 */
public class RemoteSessionRepoTest extends TestWithCurator {

    private RemoteSessionRepo remoteSessionRepo;

    @Before
    public void setupFacade() throws Exception {
        createSession(2l, false);
        createSession(3l, false);
        curator.create(Path.fromString("/applications"));
        curator.create(Path.fromString("/sessions"));
        Tenant tenant = TenantBuilder.create(new TestComponentRegistry.Builder().curator(curator).build(),
                                             TenantName.defaultName(),
                                             Path.createRoot()).build();
        this.remoteSessionRepo = tenant.getRemoteSessionRepo();
    }

    private void createSession(long sessionId, boolean wait) {
        createSession("", sessionId, wait);
    }


    private void createSession(String root, long sessionId, boolean wait) {
        Path rootPath = Path.fromString(root).append("sessions");
        curator.create(rootPath);
        SessionZooKeeperClient zkc = new SessionZooKeeperClient(curator, rootPath.append(String.valueOf(sessionId)));
        zkc.createNewSession(System.currentTimeMillis(), TimeUnit.MILLISECONDS);
        if (wait) {
            Curator.CompletionWaiter waiter = zkc.getUploadWaiter();
            waiter.awaitCompletion(Duration.ofSeconds(120));
        }
    }

    @Test
    public void testInitialize() {
        assertSessionExists(2l);
        assertSessionExists(3l);
    }

    @Test
    public void testCreateSession() throws Exception {
        createSession(0l, true);
        assertSessionExists(0l);
    }

    @Test
    public void testSessionStateChange() throws Exception {
        Path session = Path.fromString("/sessions/0");
        createSession(0l, true);
        assertSessionStatus(0l, Session.Status.NEW);
        assertStatusChange(0l, Session.Status.PREPARE);
        assertStatusChange(0l, Session.Status.ACTIVATE);

        curator.delete(session);
        assertSessionRemoved(0l);
        assertNull(remoteSessionRepo.getSession(0l));
    }

    @Test
    public void testBadApplicationRepoOnActivate() throws Exception {
        TenantApplications applicationRepo = new FailingTenantApplications();
        curator.framework().create().forPath("/mytenant");
        Tenant tenant = TenantBuilder.create(new TestComponentRegistry.Builder().curator(curator).build(),
                                             TenantName.from("mytenant"),
                                             Path.fromString("mytenant"))
                .withApplicationRepo(applicationRepo)
                .build();
        remoteSessionRepo = tenant.getRemoteSessionRepo();
        createSession("/mytenant", 2l, true);
        assertThat(remoteSessionRepo.listSessions().size(), is(1));
    }

    private void assertStatusChange(long sessionId, Session.Status status) throws Exception {
        Path statePath = Path.fromString("/sessions/" + sessionId).append(ConfigCurator.SESSIONSTATE_ZK_SUBPATH);
        curator.create(statePath);
        curatorFramework.setData().forPath(statePath.getAbsolute(), Utf8.toBytes(status.toString()));
        System.out.println("Setting status " + status + " for " + sessionId);
        assertSessionStatus(0l, status);
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
        public void close() {

        }
    }
}
