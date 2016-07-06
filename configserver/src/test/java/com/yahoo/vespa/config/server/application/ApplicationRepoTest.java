// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.application;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.TenantName;
import com.yahoo.path.Path;
import com.yahoo.text.Utf8;
import com.yahoo.vespa.config.server.MockReloadHandler;
import com.yahoo.vespa.config.server.TestWithCurator;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.*;

/**
 * @author lulf
 * @since 5.1
 */
public class ApplicationRepoTest extends TestWithCurator {

    @Test
    public void require_that_applications_are_read_from_zookeeper() throws Exception {
        curatorFramework.create().creatingParentsIfNeeded().forPath("/foo:dev:baz:bim", Utf8.toAsciiBytes(3));
        curatorFramework.create().creatingParentsIfNeeded().forPath("/bar:test:bim:quux", Utf8.toAsciiBytes(4));
        curatorFramework.create().creatingParentsIfNeeded().forPath("/bario:staging:bala:bong", Utf8.toAsciiBytes(5));
        ApplicationRepo repo = createZKAppRepo();
        List<ApplicationId> applications = repo.listApplications();
        assertThat(applications.size(), is(3));
        assertThat(applications.get(0).application().value(), is("bario"));
        assertThat(applications.get(1).application().value(), is("bar"));
        assertThat(applications.get(2).application().value(), is("foo"));
        assertThat(repo.getSessionIdForApplication(applications.get(0)), is(5l));
        assertThat(repo.getSessionIdForApplication(applications.get(1)), is(4l));
        assertThat(repo.getSessionIdForApplication(applications.get(2)), is(3l));
    }

    @Test
    public void require_that_legacy_application_ids_are_rewritten() throws Exception {
        curatorFramework.create().creatingParentsIfNeeded().forPath("/foo:default:baz:bim", Utf8.toAsciiBytes(3));
        curatorFramework.create().creatingParentsIfNeeded().forPath("/bar:test:bim:quux", Utf8.toAsciiBytes(4));
        ApplicationRepo repo = createZKAppRepo();
        List<ApplicationId> applications = repo.listApplications();
        assertThat(applications.size(), is(2));
        assertThat(applications.get(0).application().value(), is("bar"));
        assertThat(applications.get(1).application().value(), is("foo"));
        assertThat(applications.get(0).instance().value(), is("quux"));
        assertThat(applications.get(1).instance().value(), is("bim"));
        assertNotNull(curatorFramework.checkExists().forPath("/mytenant:foo:bim"));
        assertNotNull(curatorFramework.checkExists().forPath("/mytenant:foo:bim"));
        assertThat(repo.getSessionIdForApplication(applications.get(0)), is(4l));
        assertThat(repo.getSessionIdForApplication(applications.get(1)), is(3l));
    }

    @Test
    public void require_that_legacy_application_ids_are_ignored() throws Exception {
        curatorFramework.create().creatingParentsIfNeeded().forPath("/foo:default:baz:bim", Utf8.toAsciiBytes(3));
        curatorFramework.create().creatingParentsIfNeeded().forPath("/foo:prod:baz:bim", Utf8.toAsciiBytes(3));
        curatorFramework.create().creatingParentsIfNeeded().forPath("/bar:test:bim:quux", Utf8.toAsciiBytes(4));
        ApplicationRepo repo = createZKAppRepo();
        List<ApplicationId> applications = repo.listApplications();
        assertThat(applications.size(), is(2));
        assertThat(repo.getSessionIdForApplication(applications.get(0)), is(4l));
        assertThat(repo.getSessionIdForApplication(applications.get(1)), is(3l));
    }

    @Test
    public void require_that_invalid_entries_are_skipped() throws Exception {
        curatorFramework.create().creatingParentsIfNeeded().forPath("/foo:dev:baz:bim");
        curatorFramework.create().creatingParentsIfNeeded().forPath("/invalid");
        ApplicationRepo repo = createZKAppRepo();
        List<ApplicationId> applications = repo.listApplications();
        assertThat(applications.size(), is(1));
        assertThat(applications.get(0).application().value(), is("foo"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void require_that_requesting_session_for_unknown_application_throws_exception() throws Exception {
        curatorFramework.create().creatingParentsIfNeeded().forPath("/foo:dev:baz:bim");
        ApplicationRepo repo = createZKAppRepo();
        repo.getSessionIdForApplication(new ApplicationId.Builder()
                                        .tenant("exist")
                                        .applicationName("tenant").instanceName("here").build());
    }

    @Test(expected = IllegalArgumentException.class)
    public void require_that_requesting_session_for_empty_application_throws_exception() throws Exception {
        curatorFramework.create().creatingParentsIfNeeded().forPath("/foo:dev:baz:bim");
        ApplicationRepo repo = createZKAppRepo();
        repo.getSessionIdForApplication(new ApplicationId.Builder()
                                        .tenant("tenant")
                                        .applicationName("foo").instanceName("bim").build());
    }

    @Test
    public void require_that_application_ids_can_be_written() throws Exception {
        ApplicationRepo repo = createZKAppRepo();
        repo.createPutApplicationTransaction(createAppplicationId("myapp"), 3l).commit();
        String path = "/mytenant:myapp:myinst";
        assertTrue(curatorFramework.checkExists().forPath(path) != null);
        assertThat(Utf8.toString(curatorFramework.getData().forPath(path)), is("3"));
        repo.createPutApplicationTransaction(createAppplicationId("myapp"), 5l).commit();
        assertTrue(curatorFramework.checkExists().forPath(path) != null);
        assertThat(Utf8.toString(curatorFramework.getData().forPath(path)), is("5"));
    }

    @Test
    public void require_that_application_ids_can_be_deleted() throws Exception {
        ApplicationRepo repo = createZKAppRepo();
        ApplicationId id1 = createAppplicationId("myapp");
        ApplicationId id2 = createAppplicationId("myapp2");
        repo.createPutApplicationTransaction(id1, 1).commit();
        repo.createPutApplicationTransaction(id2, 1).commit();
        assertThat(repo.listApplications().size(), is(2));
        System.out.println("------ Test deleting " + id1);
        repo.deleteApplication(id1).commit();
        assertThat(repo.listApplications().size(), is(1));
        System.out.println("------ Test deleting " + id2);
        repo.deleteApplication(id2).commit();
        assertThat(repo.listApplications().size(), is(0));
        System.out.println("------ Test deleting " + id2);
        repo.deleteApplication(id2).commit();
        assertThat(repo.listApplications().size(), is(0));
    }

    @Test
    public void require_that_repos_behave_similarly() throws Exception {
        ApplicationRepo zkRepo = createZKAppRepo();
        ApplicationRepo memRepo = new MemoryApplicationRepo();
        for (ApplicationRepo repo : Arrays.asList(zkRepo, memRepo)) {
            ApplicationId id1 = createAppplicationId("myapp");
            ApplicationId id2 = createAppplicationId("myapp2");
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
            assertThat(repo.listApplications().size(), is(0));
            repo.deleteApplication(id2).commit();
        }
    }

    @Test
    public void require_that_reload_handler_is_called_when_apps_are_removed() throws Exception {
        curatorFramework.create().creatingParentsIfNeeded().forPath("/foo:test:baz:bim", Utf8.toAsciiBytes(3));
        curatorFramework.create().creatingParentsIfNeeded().forPath("/bar:dev:bim:quux", Utf8.toAsciiBytes(4));
        curatorFramework.create().creatingParentsIfNeeded().forPath("/bario:staging:bala:bong", Utf8.toAsciiBytes(5));
        MockReloadHandler reloadHandler = new MockReloadHandler();
        ApplicationRepo repo = createZKAppRepo(reloadHandler);
        assertNull(reloadHandler.lastRemoved);
        repo.deleteApplication(new ApplicationId.Builder()
                               .tenant("mytenant")
                               .applicationName("bar").instanceName("quux").build())
                .commit();
        long endTime = System.currentTimeMillis() + 60_000;
        while (System.currentTimeMillis() < endTime && reloadHandler.lastRemoved == null) {
            Thread.sleep(100);
        }
        assertNotNull(reloadHandler.lastRemoved);
        assertThat(reloadHandler.lastRemoved.serializedForm(), is("mytenant:bar:quux"));
    }

    private ApplicationRepo createZKAppRepo() {
        return createZKAppRepo(new MockReloadHandler());
    }

    private ApplicationRepo createZKAppRepo(MockReloadHandler reloadHandler) {
        return ZKApplicationRepo.create(curator, Path.createRoot(), reloadHandler, TenantName.from("mytenant"));
    }

    private static ApplicationId createAppplicationId(String name) {
        return new ApplicationId.Builder()
            .tenant("mytenant")
            .applicationName(name).instanceName("myinst").build();
    }
}
