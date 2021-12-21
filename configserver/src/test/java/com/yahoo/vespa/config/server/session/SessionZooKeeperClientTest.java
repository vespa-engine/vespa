// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.session;

import com.yahoo.config.FileReference;
import com.yahoo.config.model.api.Quota;
import com.yahoo.config.model.api.TenantSecretStore;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.TenantName;
import com.yahoo.path.Path;
import com.yahoo.text.Utf8;
import com.yahoo.vespa.config.server.tenant.TenantRepository;
import com.yahoo.vespa.config.util.ConfigUtils;
import com.yahoo.vespa.curator.Curator;
import com.yahoo.vespa.curator.mock.MockCurator;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static com.yahoo.vespa.config.server.zookeeper.ZKApplication.SESSIONSTATE_ZK_SUBPATH;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author Ulf Lilleengen
 */
public class SessionZooKeeperClientTest {

    private static final TenantName tenantName = TenantName.defaultName();

    private Curator curator;

    @SuppressWarnings("deprecation")
    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Before
    public void setup() {
        curator = new MockCurator();
        curator.create(sessionsPath());
    }

    @Test
    public void require_that_status_can_be_updated() {
        SessionZooKeeperClient zkc = createSessionZKClient(1);
        zkc.writeStatus(Session.Status.NEW);
        assertEquals(Session.Status.NEW, zkc.readStatus());

        zkc.writeStatus(Session.Status.PREPARE);
        assertEquals(Session.Status.PREPARE, zkc.readStatus());

        zkc.writeStatus(Session.Status.ACTIVATE);
        assertEquals(Session.Status.ACTIVATE, zkc.readStatus());

        zkc.writeStatus(Session.Status.DEACTIVATE);
        assertEquals(Session.Status.DEACTIVATE, zkc.readStatus());
    }

    @Test
    public void require_that_status_is_written_to_zk() {
        int sessionId = 2;
        SessionZooKeeperClient zkc = createSessionZKClient(sessionId);
        zkc.writeStatus(Session.Status.NEW);
        Path path = sessionPath(sessionId).append(SESSIONSTATE_ZK_SUBPATH);
        assertTrue(curator.exists(path));
        assertEquals("NEW", Utf8.toString(curator.getData(path).get()));
    }

    @Test
    public void require_that_status_is_read_from_zk() {
        int sessionId = 3;
        SessionZooKeeperClient zkc = createSessionZKClient(sessionId);
        curator.set(sessionPath(sessionId).append(SESSIONSTATE_ZK_SUBPATH), Utf8.toBytes("PREPARE"));
        assertEquals(Session.Status.PREPARE, zkc.readStatus());
    }

    @Test
    public void require_that_application_id_is_written_to_zk() {
        ApplicationId id = new ApplicationId.Builder()
                .tenant(tenantName)
                .applicationName("foo")
                .instanceName("bim")
                .build();
        int sessionId = 3;
        SessionZooKeeperClient zkc = createSessionZKClient(sessionId);
        zkc.writeApplicationId(id);
        Path path = sessionPath(sessionId).append(SessionZooKeeperClient.APPLICATION_ID_PATH);
        assertTrue(curator.exists(path));
        assertEquals(id.serializedForm(), Utf8.toString(curator.getData(path).get()));
    }

    @Test
    public void require_that_wrong_application_gives_exception() {
        ApplicationId id = new ApplicationId.Builder()
                .tenant("someOtherTenant")
                .applicationName("foo")
                .instanceName("bim")
                .build();
        int sessionId = 3;
        SessionZooKeeperClient zkc = createSessionZKClient(sessionId);

        expectedException.expect(IllegalArgumentException.class);
        expectedException.expectMessage("Cannot write application id 'someOtherTenant.foo.bim' for tenant 'default'");
        zkc.writeApplicationId(id);
    }

    @Test
    public void require_that_application_id_is_read_from_zk() {
        ApplicationId id = new ApplicationId.Builder()
                .tenant("tenant")
                .applicationName("bar")
                .instanceName("quux")
                .build();
        String idNoVersion = id.serializedForm();
        assertApplicationIdParse(3, idNoVersion, idNoVersion);
    }

    @Test
    public void require_that_create_time_can_be_written_and_read() {
        int sessionId = 3;
        SessionZooKeeperClient zkc = createSessionZKClient(sessionId);
        curator.delete(sessionPath(sessionId));
        assertEquals(Instant.EPOCH, zkc.readCreateTime());
        Instant now = Instant.now();
        zkc.createNewSession(now);
        // resolution is in seconds, so need to go back use that when comparing
        assertEquals(Instant.ofEpochSecond(now.getEpochSecond()), zkc.readCreateTime());
    }

    @Test
    public void require_that_application_package_file_reference_can_be_written_and_read() {
        final FileReference testRef = new FileReference("test-ref");
        SessionZooKeeperClient zkc = createSessionZKClient(3);
        zkc.writeApplicationPackageReference(Optional.of(testRef));
        assertEquals(testRef, zkc.readApplicationPackageReference());
    }

    @Test
    public void require_quota_written_and_parsed() {
        var quota = Optional.of(new Quota(Optional.of(23), Optional.of(32)));
        var zkc = createSessionZKClient(4);
        zkc.writeQuota(quota);
        assertEquals(quota, zkc.readQuota());
    }

    @Test
    public void require_tenant_secret_stores_written_and_parsed() {
        var secretStores = List.of(
                new TenantSecretStore("name1", "awsId1", "role1"),
                new TenantSecretStore("name2", "awsId2", "role2")
        );
        var zkc = createSessionZKClient(4);
        zkc.writeTenantSecretStores(secretStores);
        assertEquals(secretStores, zkc.readTenantSecretStores());
    }

    private void assertApplicationIdParse(long sessionId, String idString, String expectedIdString) {
        SessionZooKeeperClient zkc = createSessionZKClient(sessionId);
        Path path = sessionPath(sessionId).append(SessionZooKeeperClient.APPLICATION_ID_PATH);
        curator.set(path, Utf8.toBytes(idString));
        ApplicationId applicationId = zkc.readApplicationId().get();
        assertEquals(expectedIdString, applicationId.serializedForm());
    }

    private SessionZooKeeperClient createSessionZKClient(long sessionId) {
        SessionZooKeeperClient zkc = new SessionZooKeeperClient(curator,
                                                                tenantName,
                                                                sessionId,
                                                                ConfigUtils.getCanonicalHostName());
        zkc.createNewSession(Instant.now());
        return zkc;
    }

    private static Path sessionsPath() {
        return TenantRepository.getSessionsPath(tenantName);
    }

    private static Path sessionPath(long sessionId) {
        return sessionsPath().append(String.valueOf(sessionId));
    }

}
