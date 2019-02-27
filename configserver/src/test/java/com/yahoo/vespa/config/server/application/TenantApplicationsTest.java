// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.application;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.TenantName;
import com.yahoo.text.Utf8;
import com.yahoo.vespa.config.server.MockReloadHandler;

import com.yahoo.vespa.config.server.tenant.TenantRepository;
import com.yahoo.vespa.curator.Curator;
import com.yahoo.vespa.curator.mock.MockCurator;
import org.apache.curator.framework.CuratorFramework;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.*;

/**
 * @author Ulf Lilleengen
 */
public class TenantApplicationsTest {

    private static final TenantName tenantName = TenantName.from("tenant");

    private Curator curator;
    private CuratorFramework curatorFramework;

    @Before
    public void setup() {
        curator = new MockCurator();
        curatorFramework = curator.framework();
    }

    @Test
    public void require_that_applications_are_read_from_zookeeper() throws Exception {
        writeApplicationData(createApplicationId("foo"), 3L);
        writeApplicationData(createApplicationId("bar"), 4L);
        TenantApplications repo = createZKAppRepo();
        List<ApplicationId> applications = repo.activeApplications();
        assertThat(applications.size(), is(2));
        assertThat(applications.get(0).application().value(), is("foo"));
        assertThat(applications.get(1).application().value(), is("bar"));
        assertThat(repo.requireActiveSessionOf(applications.get(0)), is(3L));
        assertThat(repo.requireActiveSessionOf(applications.get(1)), is(4L));
    }

    @Test
    public void require_that_invalid_entries_are_skipped() throws Exception {
        writeApplicationData(createApplicationId("foo"), 3L);
        writeApplicationData("invalid", 3L);
        TenantApplications repo = createZKAppRepo();
        List<ApplicationId> applications = repo.activeApplications();
        assertThat(applications.size(), is(1));
        assertThat(applications.get(0).application().value(), is("foo"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void require_that_requesting_session_for_unknown_application_throws_exception() throws Exception {
        TenantApplications repo = createZKAppRepo();
        repo.requireActiveSessionOf(createApplicationId("nonexistent"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void require_that_requesting_session_for_empty_application_throws_exception() throws Exception {
        ApplicationId baz = createApplicationId("baz");
        // No data in node
        curatorFramework.create().creatingParentsIfNeeded()
                .forPath(TenantRepository.getApplicationsPath(tenantName).append(baz.serializedForm()).getAbsolute());
        TenantApplications repo = createZKAppRepo();
        repo.requireActiveSessionOf(baz);
    }

    @Test
    public void require_that_application_ids_can_be_written() throws Exception {
        TenantApplications repo = createZKAppRepo();
        ApplicationId myapp = createApplicationId("myapp");
        repo.createPutTransaction(myapp, 3l).commit();
        String path = TenantRepository.getApplicationsPath(tenantName).append(myapp.serializedForm()).getAbsolute();
        assertTrue(curatorFramework.checkExists().forPath(path) != null);
        assertThat(Utf8.toString(curatorFramework.getData().forPath(path)), is("3"));
        repo.createPutTransaction(myapp, 5l).commit();
        assertTrue(curatorFramework.checkExists().forPath(path) != null);
        assertThat(Utf8.toString(curatorFramework.getData().forPath(path)), is("5"));
    }

    @Test
    public void require_that_application_ids_can_be_deleted() throws Exception {
        TenantApplications repo = createZKAppRepo();
        ApplicationId id1 = createApplicationId("myapp");
        ApplicationId id2 = createApplicationId("myapp2");
        repo.createPutTransaction(id1, 1).commit();
        repo.createPutTransaction(id2, 1).commit();
        assertThat(repo.activeApplications().size(), is(2));
        repo.createDeleteTransaction(id1).commit();
        assertThat(repo.activeApplications().size(), is(1));
        repo.createDeleteTransaction(id2).commit();
        assertThat(repo.activeApplications().size(), is(0));
    }

    @Test
    public void require_that_reload_handler_is_called_when_apps_are_removed() throws Exception {
        ApplicationId foo = createApplicationId("foo");
        writeApplicationData(foo, 3L);
        writeApplicationData(createApplicationId("bar"), 4L);
        MockReloadHandler reloadHandler = new MockReloadHandler();
        TenantApplications repo = createZKAppRepo(reloadHandler);
        assertNull(reloadHandler.lastRemoved);
        repo.createDeleteTransaction(foo).commit();
        long endTime = System.currentTimeMillis() + 60_000;
        while (System.currentTimeMillis() < endTime && reloadHandler.lastRemoved == null) {
            Thread.sleep(100);
        }
        assertNotNull(reloadHandler.lastRemoved);
        assertThat(reloadHandler.lastRemoved.serializedForm(), is(foo.serializedForm()));
    }

    private TenantApplications createZKAppRepo() {
        return createZKAppRepo(new MockReloadHandler());
    }

    private TenantApplications createZKAppRepo(MockReloadHandler reloadHandler) {
        return TenantApplications.create(curator, reloadHandler, tenantName);
    }

    private static ApplicationId createApplicationId(String name) {
        return new ApplicationId.Builder()
                .tenant(tenantName.value())
                .applicationName(name)
                .instanceName("myinst")
                .build();
    }

    private void writeApplicationData(ApplicationId applicationId, long sessionId) throws Exception {
        writeApplicationData(applicationId.serializedForm(), sessionId);
    }

    private void writeApplicationData(String applicationId, long sessionId) throws Exception {
        curatorFramework
                .create()
                .creatingParentsIfNeeded()
                .forPath(TenantRepository.getApplicationsPath(tenantName).append(applicationId).getAbsolute(),
                         Utf8.toAsciiBytes(sessionId));
    }
}
