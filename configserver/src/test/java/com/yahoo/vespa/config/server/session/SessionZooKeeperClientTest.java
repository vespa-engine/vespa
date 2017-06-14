// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.session;

import com.yahoo.config.provision.TenantName;
import com.yahoo.path.Path;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.text.Utf8;
import com.yahoo.vespa.config.server.TestWithCurator;
import com.yahoo.vespa.config.server.zookeeper.ConfigCurator;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

/**
 * @author lulf
 * @since 5.1
 */
public class SessionZooKeeperClientTest extends TestWithCurator {

    @Test
    public void require_that_status_can_be_updated() {
        SessionZooKeeperClient zkc = createSessionZKClient("1");
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
        SessionZooKeeperClient zkc = createSessionZKClient("2");
        zkc.writeStatus(Session.Status.NEW);
        String path = "/2" + ConfigCurator.SESSIONSTATE_ZK_SUBPATH;
        assertTrue(configCurator.exists(path));
        assertThat(configCurator.getData(path), is("NEW"));
    }

    @Test
    public void require_that_status_is_read_from_zk() {
        SessionZooKeeperClient zkc = createSessionZKClient("3");
        curator.set(Path.fromString("3").append(ConfigCurator.SESSIONSTATE_ZK_SUBPATH), Utf8.toBytes("PREPARE"));
        assertThat(zkc.readStatus(), is(Session.Status.PREPARE));
    }

    @Test
    public void require_that_application_id_is_written_to_zk() {
        ApplicationId id = new ApplicationId.Builder()
                           .tenant("tenant")
                           .applicationName("foo").instanceName("bim").build();
        SessionZooKeeperClient zkc = createSessionZKClient("3");
        zkc.writeApplicationId(id);
        String path = "/3/" + SessionZooKeeperClient.APPLICATION_ID_PATH;
        assertTrue(configCurator.exists(path));
        assertThat(configCurator.getData(path), is("tenant:foo:bim"));
    }

    @Test
    public void require_that_application_id_is_read_from_zk() {
        ApplicationId id = new ApplicationId.Builder()
                           .tenant("tenant")
                           .applicationName("bar").instanceName("quux").build();
        String idNoVersion = id.serializedForm();
        assertApplicationIdParse("3", idNoVersion, idNoVersion);
    }

    @Test
    public void require_that_default_name_is_returned_if_node_does_not_exist() {
        assertThat(createSessionZKClient("3").readApplicationId().application().value(), is("default"));
    }

    @Test
    public void require_that_create_time_can_be_written_and_read() {
        SessionZooKeeperClient zkc = createSessionZKClient("3");
        curator.delete(Path.fromString("3"));
        assertThat(zkc.readCreateTime(), is(0l));
        zkc.createNewSession(123456l, TimeUnit.SECONDS);
        assertThat(zkc.readCreateTime(), is(123456l));
    }

    @Test
    public void require_that_create_time_has_correct_unit() {
        SessionZooKeeperClient zkc = createSessionZKClient("3");
        curator.delete(Path.fromString("3"));
        assertThat(zkc.readCreateTime(), is(0l));
        zkc.createNewSession(60, TimeUnit.MINUTES);
        assertThat(zkc.readCreateTime(), is(3600l));
    }

    private void assertApplicationIdParse(String sessionId, String idString, String expectedIdString) {
        SessionZooKeeperClient zkc = createSessionZKClient(sessionId);
        String path = "/" + sessionId + "/" + SessionZooKeeperClient.APPLICATION_ID_PATH;
        configCurator.putData(path, idString);
        ApplicationId zkId = zkc.readApplicationId();
        assertThat(zkId.serializedForm(), is(expectedIdString));
    }

    private SessionZooKeeperClient createSessionZKClient(String generation) {
        return createSessionZKClient(generation, 100);
    }

    private SessionZooKeeperClient createSessionZKClient(String generation, long createTimeInMillis) {
        SessionZooKeeperClient zkc = new SessionZooKeeperClient(curator, Path.fromString(generation));
        zkc.createNewSession(createTimeInMillis, TimeUnit.MILLISECONDS);
        return zkc;
    }

}
