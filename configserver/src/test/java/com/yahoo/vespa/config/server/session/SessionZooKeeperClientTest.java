// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.session;

import com.yahoo.config.FileReference;
import com.yahoo.config.model.api.Quota;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.TenantName;
import com.yahoo.path.Path;
import com.yahoo.text.Utf8;
import com.yahoo.vespa.config.server.tenant.TenantRepository;
import com.yahoo.vespa.config.server.zookeeper.ConfigCurator;
import com.yahoo.vespa.config.util.ConfigUtils;
import com.yahoo.vespa.curator.Curator;
import com.yahoo.vespa.curator.mock.MockCurator;
import org.junit.Before;
import org.junit.Test;

import java.time.Instant;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * @author Ulf Lilleengen
 */
public class SessionZooKeeperClientTest {

    private static final TenantName tenantName = TenantName.defaultName();

    private Curator curator;
    private ConfigCurator configCurator;

    @Before
    public void setup() {
        curator = new MockCurator();
        configCurator = ConfigCurator.create(curator);
        curator.create(sessionsPath());
    }

    @Test
    public void require_that_status_can_be_updated() {
        SessionZooKeeperClient zkc = createSessionZKClient(1);
        zkc.writeStatus(Session.Status.NEW);
        assertThat(zkc.readStatus(), is(Session.Status.NEW));

        zkc.writeStatus(Session.Status.PREPARE);
        assertThat(zkc.readStatus(), is(Session.Status.PREPARE));

        zkc.writeStatus(Session.Status.ACTIVATE);
        assertThat(zkc.readStatus(), is(Session.Status.ACTIVATE));

        zkc.writeStatus(Session.Status.DEACTIVATE);
        assertThat(zkc.readStatus(), is(Session.Status.DEACTIVATE));
    }

    @Test
    public void require_that_status_is_written_to_zk() {
        int sessionId = 2;
        SessionZooKeeperClient zkc = createSessionZKClient(sessionId);
        zkc.writeStatus(Session.Status.NEW);
        String path = sessionPath(sessionId).append(ConfigCurator.SESSIONSTATE_ZK_SUBPATH).getAbsolute();
        assertTrue(configCurator.exists(path));
        assertThat(configCurator.getData(path), is("NEW"));
    }

    @Test
    public void require_that_status_is_read_from_zk() {
        int sessionId = 3;
        SessionZooKeeperClient zkc = createSessionZKClient(sessionId);
        curator.set(sessionPath(sessionId).append(ConfigCurator.SESSIONSTATE_ZK_SUBPATH), Utf8.toBytes("PREPARE"));
        assertThat(zkc.readStatus(), is(Session.Status.PREPARE));
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
        String path = sessionPath(sessionId).append(SessionZooKeeperClient.APPLICATION_ID_PATH).getAbsolute();
        assertTrue(configCurator.exists(path));
        assertThat(configCurator.getData(path), is(id.serializedForm()));
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

        Exception e = assertThrows(IllegalArgumentException.class, () -> zkc.writeApplicationId(id));
        assertEquals("Cannot write application id 'someOtherTenant.foo.bim' for tenant 'default'", e.getMessage());
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
        assertThat(zkc.readCreateTime(), is(Instant.EPOCH));
        Instant now = Instant.now();
        zkc.createNewSession(now);
        // resolution is in seconds, so need to go back use that when comparing
        assertThat(zkc.readCreateTime(), is(Instant.ofEpochSecond(now.getEpochSecond())));
    }

    @Test
    public void require_that_application_package_file_reference_can_be_written_and_read() {
        final FileReference testRef = new FileReference("test-ref");
        SessionZooKeeperClient zkc = createSessionZKClient(3);
        zkc.writeApplicationPackageReference(Optional.of(testRef));
        assertThat(zkc.readApplicationPackageReference(), is(testRef));
    }

    @Test
    public void require_quota_written_and_parsed() {
        var quota = Optional.of(new Quota(Optional.of(23), Optional.of(32)));
        var zkc = createSessionZKClient(4);
        zkc.writeQuota(quota);
        assertEquals(quota, zkc.readQuota());
    }

    private void assertApplicationIdParse(long sessionId, String idString, String expectedIdString) {
        SessionZooKeeperClient zkc = createSessionZKClient(sessionId);
        String path = sessionPath(sessionId).append(SessionZooKeeperClient.APPLICATION_ID_PATH).getAbsolute();
        configCurator.putData(path, idString);
        ApplicationId applicationId = zkc.readApplicationId().get();
        assertThat(applicationId.serializedForm(), is(expectedIdString));
    }

    private SessionZooKeeperClient createSessionZKClient(long sessionId) {
        SessionZooKeeperClient zkc = new SessionZooKeeperClient(curator,
                                                                ConfigCurator.create(curator),
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
