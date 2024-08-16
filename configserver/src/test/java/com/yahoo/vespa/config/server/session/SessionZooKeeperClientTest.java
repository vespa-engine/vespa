// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.session;

import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.component.Version;
import com.yahoo.config.FileReference;
import com.yahoo.config.model.api.Quota;
import com.yahoo.config.model.api.TenantSecretStore;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.TenantName;
import com.yahoo.path.Path;
import com.yahoo.text.Utf8;
import com.yahoo.vespa.config.server.tenant.TenantRepository;
import com.yahoo.vespa.curator.Curator;
import com.yahoo.vespa.curator.mock.MockCurator;
import org.junit.Before;
import org.junit.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static com.yahoo.vespa.config.server.session.SessionData.APPLICATION_ID_PATH;
import static com.yahoo.vespa.config.server.session.SessionData.SESSION_DATA_PATH;
import static com.yahoo.vespa.config.server.zookeeper.ZKApplication.SESSIONSTATE_ZK_SUBPATH;
import static java.math.BigDecimal.valueOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * @author Ulf Lilleengen
 */
public class SessionZooKeeperClientTest {

    private static final TenantName tenantName = TenantName.defaultName();

    private Curator curator;

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
        Path path = sessionPath(sessionId).append(APPLICATION_ID_PATH);
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

        assertEquals("Cannot write application id 'someOtherTenant.foo.bim' for tenant 'default'",
                     assertThrows(IllegalArgumentException.class,
                                  () -> zkc.writeApplicationId(id)).getMessage());
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
        assertEquals(testRef, zkc.readApplicationPackageReference().get());
    }

    @Test
    public void require_quota_written_and_parsed() {
        var quota = Optional.of(new Quota(Optional.of(23), Optional.of(valueOf(32))));
        var zkc = createSessionZKClient(4);
        zkc.writeQuota(quota);
        assertEquals(quota, zkc.readQuota());
    }

    @Test
    public void require_tenant_secret_stores_written_and_parsed() {
        var secretStores = List.of(
                new TenantSecretStore("name1", "awsId1", "role1"),
                new TenantSecretStore("name2", "awsId2", "role2", "extId2")
        );
        var zkc = createSessionZKClient(4);
        zkc.writeTenantSecretStores(secretStores);
        List<TenantSecretStore> actual = zkc.readTenantSecretStores();
        assertEquals(secretStores, actual);
    }

    @Test
    public void require_that_session_data_is_written_to_zk() {
        int sessionId = 2;
        SessionZooKeeperClient zkc = createSessionZKClient(sessionId);
        zkc.writeSessionData(new SessionData(ApplicationId.defaultId(),
                                             Optional.of(new FileReference("foo")),
                                             Version.fromString("8.195.1"),
                                             Instant.now(),
                                             Optional.empty(),
                                             Optional.empty(),
                                             Optional.empty(),
                                             List.of(),
                                             List.of(),
                                             Optional.empty(),
                                             List.of(),
                                             ActivationTriggers.empty()));
        Path path = sessionPath(sessionId).append(SESSION_DATA_PATH);
        assertTrue(curator.exists(path));
        String data = Utf8.toString(curator.getData(path).get());
        assertTrue(data.contains("{\"applicationId\":\"default:default:default\",\"applicationPackageReference\":\"foo\",\"version\":\"8.195.1\",\"createTime\":"));
        assertTrue(data.contains(",\"tenantSecretStores\":[],\"operatorCertificates\":[],\"dataplaneTokens\":[]," +
                                 "\"activationTriggers\":{\"nodeRestarts\":[],\"reindexings\":[]}"));
    }

    private void assertApplicationIdParse(long sessionId, String idString, String expectedIdString) {
        SessionZooKeeperClient zkc = createSessionZKClient(sessionId);
        Path path = sessionPath(sessionId).append(APPLICATION_ID_PATH);
        curator.set(path, Utf8.toBytes(idString));
        assertEquals(expectedIdString, zkc.readApplicationId().serializedForm());
    }

    private SessionZooKeeperClient createSessionZKClient(long sessionId) {
        SessionZooKeeperClient zkc = new SessionZooKeeperClient(curator,
                                                                tenantName,
                                                                sessionId,
                                                                new ConfigserverConfig.Builder().build());
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
