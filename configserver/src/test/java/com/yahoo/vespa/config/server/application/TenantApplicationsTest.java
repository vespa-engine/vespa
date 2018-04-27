// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.application;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.TenantName;
import com.yahoo.text.Utf8;
import com.yahoo.vespa.config.server.MockReloadHandler;
import com.yahoo.vespa.config.server.TestWithCurator;

import com.yahoo.vespa.config.server.tenant.TenantRepository;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.*;

/**
 * @author Ulf Lilleengen
 * @since 5.1
 */
public class TenantApplicationsTest extends TestWithCurator {

    private static final TenantName tenantName = TenantName.from("tenant");

    @Test
    public void require_that_applications_are_read_from_zookeeper() throws Exception {
        writeApplicationData(createApplicationId("foo"), 3L);
        writeApplicationData(createApplicationId("bar"), 4L);
        TenantApplications repo = createZKAppRepo();
        List<ApplicationId> applications = repo.listApplications();
        assertThat(applications.size(), is(2));
        assertThat(applications.get(0).application().value(), is("foo"));
        assertThat(applications.get(1).application().value(), is("bar"));
        assertThat(repo.getSessionIdForApplication(applications.get(0)), is(3L));
        assertThat(repo.getSessionIdForApplication(applications.get(1)), is(4L));
    }

    @Test
    public void require_that_invalid_entries_are_skipped() throws Exception {
        writeApplicationData(createApplicationId("foo"), 3L);
        writeApplicationData("invalid", 3L);
        TenantApplications repo = createZKAppRepo();
        List<ApplicationId> applications = repo.listApplications();
        assertThat(applications.size(), is(1));
        assertThat(applications.get(0).application().value(), is("foo"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void require_that_requesting_session_for_unknown_application_throws_exception() throws Exception {
        TenantApplications repo = createZKAppRepo();
        repo.getSessionIdForApplication(createApplicationId("nonexistent"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void require_that_requesting_session_for_empty_application_throws_exception() throws Exception {
        ApplicationId baz = createApplicationId("baz");
        // No data in node
        curatorFramework.create().creatingParentsIfNeeded()
                .forPath(TenantRepository.getApplicationsPath(tenantName).append(baz.serializedForm()).getAbsolute());
        TenantApplications repo = createZKAppRepo();
        repo.getSessionIdForApplication(baz);
    }

    @Test
    public void require_that_application_ids_can_be_written() throws Exception {
        TenantApplications repo = createZKAppRepo();
        ApplicationId myapp = createApplicationId("myapp");
        repo.createPutApplicationTransaction(myapp, 3l).commit();
        String path = TenantRepository.getApplicationsPath(tenantName).append(myapp.serializedForm()).getAbsolute();
        assertTrue(curatorFramework.checkExists().forPath(path) != null);
        assertThat(Utf8.toString(curatorFramework.getData().forPath(path)), is("3"));
        repo.createPutApplicationTransaction(myapp, 5l).commit();
        assertTrue(curatorFramework.checkExists().forPath(path) != null);
        assertThat(Utf8.toString(curatorFramework.getData().forPath(path)), is("5"));
    }

    @Test
    public void require_that_application_ids_can_be_deleted() throws Exception {
        TenantApplications repo = createZKAppRepo();
        ApplicationId id1 = createApplicationId("myapp");
        ApplicationId id2 = createApplicationId("myapp2");
        repo.createPutApplicationTransaction(id1, 1).commit();
        repo.createPutApplicationTransaction(id2, 1).commit();
        assertThat(repo.listApplications().size(), is(2));
        repo.deleteApplication(id1).commit();
        assertThat(repo.listApplications().size(), is(1));
        repo.deleteApplication(id2).commit();
        assertThat(repo.listApplications().size(), is(0));
    }

    @Test
    public void require_that_repos_behave_similarly() throws Exception {
        TenantApplications zkRepo = createZKAppRepo();
        TenantApplications memRepo = new MemoryTenantApplications();
        for (TenantApplications repo : Arrays.asList(zkRepo, memRepo)) {
            ApplicationId id1 = createApplicationId("myapp");
            ApplicationId id2 = createApplicationId("myapp2");
            repo.createPutApplicationTransaction(id1, 4).commit();
            repo.createPutApplicationTransaction(id2, 5).commit();
            List<ApplicationId> lst = repo.listApplications();
            Collections.sort(lst);
            assertThat(lst.size(), is(2));
            assertThat(lst.get(0).application(), is(id1.application()));
            assertThat(lst.get(1).application(), is(id2.application()));
            assertThat(repo.getSessionIdForApplication(id1), is(4l));
            assertThat(repo.getSessionIdForApplication(id2), is(5l));
            repo.createPutApplicationTransaction(id1, 6).commit();
            lst = repo.listApplications();
            Collections.sort(lst);
            assertThat(lst.size(), is(2));
            assertThat(lst.get(0).application(), is(id1.application()));
            assertThat(lst.get(1).application(), is(id2.application()));
            assertThat(repo.getSessionIdForApplication(id1), is(6l));
            assertThat(repo.getSessionIdForApplication(id2), is(5l));
            repo.deleteApplication(id1).commit();
            assertThat(repo.listApplications().size(), is(1));
            repo.deleteApplication(id2).commit();
        }
    }

    @Test
    public void require_that_reload_handler_is_called_when_apps_are_removed() throws Exception {
        ApplicationId foo = createApplicationId("foo");
        writeApplicationData(foo, 3L);
        writeApplicationData(createApplicationId("bar"), 4L);
        MockReloadHandler reloadHandler = new MockReloadHandler();
        TenantApplications repo = createZKAppRepo(reloadHandler);
        assertNull(reloadHandler.lastRemoved);
        repo.deleteApplication(foo).commit();
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
        return ZKTenantApplications.create(curator, reloadHandler, tenantName);
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
