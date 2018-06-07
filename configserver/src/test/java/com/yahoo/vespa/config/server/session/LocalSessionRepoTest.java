// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.session;

import com.yahoo.config.model.application.provider.FilesApplicationPackage;
import com.yahoo.test.ManualClock;
import com.yahoo.config.provision.TenantName;
import com.yahoo.vespa.config.server.GlobalComponentRegistry;
import com.yahoo.vespa.config.server.TestComponentRegistry;
import com.yahoo.vespa.config.server.application.MemoryTenantApplications;
import com.yahoo.vespa.config.server.deploy.TenantFileSystemDirs;
import com.yahoo.io.IOUtils;
import com.yahoo.vespa.config.server.host.HostRegistry;
import com.yahoo.vespa.config.server.http.SessionHandlerTest;

import com.yahoo.vespa.curator.mock.MockCurator;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.time.Duration;
import java.time.Instant;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

/**
 * @author Ulf Lilleengen
 */
public class LocalSessionRepoTest {

    private File testApp = new File("src/test/apps/app");
    private LocalSessionRepo repo;
    private ManualClock clock;
    private static final TenantName tenantName = TenantName.defaultName();

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Before
    public void setupSessions() throws Exception {
        setupSessions(tenantName, true);
    }

    private void setupSessions(TenantName tenantName, boolean createInitialSessions) throws Exception {
        GlobalComponentRegistry globalComponentRegistry = new TestComponentRegistry.Builder().curator(new MockCurator()).build();
        TenantFileSystemDirs tenantFileSystemDirs = new TenantFileSystemDirs(temporaryFolder.newFolder(), tenantName);
        if (createInitialSessions) {
            IOUtils.copyDirectory(testApp, new File(tenantFileSystemDirs.sessionsPath(), "1"));
            IOUtils.copyDirectory(testApp, new File(tenantFileSystemDirs.sessionsPath(), "2"));
            IOUtils.copyDirectory(testApp, new File(tenantFileSystemDirs.sessionsPath(), "3"));
        }
        clock = new ManualClock(Instant.ofEpochSecond(1));
        LocalSessionLoader loader = new SessionFactoryImpl(globalComponentRegistry,
                                                           new MemoryTenantApplications(),
                                                           tenantFileSystemDirs, new HostRegistry<>(),
                                                           tenantName);
        repo = new LocalSessionRepo(tenantFileSystemDirs, loader, clock, 5);
    }

    @Test
    public void require_that_sessions_can_be_loaded_from_disk() {
        assertNotNull(repo.getSession(1l));
        assertNotNull(repo.getSession(2l));
        assertNotNull(repo.getSession(3l));
        assertNull(repo.getSession(4l));
    }

    @Test
    public void require_that_old_sessions_are_purged() {
        clock.advance(Duration.ofSeconds(1));
        assertNotNull(repo.getSession(1l));
        assertNotNull(repo.getSession(2l));
        assertNotNull(repo.getSession(3l));
        clock.advance(Duration.ofSeconds(1));
        assertNotNull(repo.getSession(1l));
        assertNotNull(repo.getSession(2l));
        assertNotNull(repo.getSession(3l));
        clock.advance(Duration.ofSeconds(1));
        addSession(4l, 6);
        assertNotNull(repo.getSession(1l));
        assertNotNull(repo.getSession(2l));
        assertNotNull(repo.getSession(3l));
        assertNotNull(repo.getSession(4l));
        clock.advance(Duration.ofSeconds(1));
        addSession(5l, 10);
        repo.purgeOldSessions();
        assertNull(repo.getSession(1l));
        assertNull(repo.getSession(2l));
        assertNull(repo.getSession(3l));
    }

    @Test
    public void require_that_all_sessions_are_deleted() {
        repo.deleteAllSessions();
        assertNull(repo.getSession(1l));
        assertNull(repo.getSession(2l));
        assertNull(repo.getSession(3l));
    }

    private void addSession(long sessionId, long createTime) {
        repo.addSession(new SessionHandlerTest.MockSession(sessionId, FilesApplicationPackage.fromFile(testApp), createTime));
    }

    @Test
    public void require_that_sessions_belong_to_a_tenant() {
        // tenant is "default"
        assertNotNull(repo.getSession(1l));
        assertNotNull(repo.getSession(2l));
        assertNotNull(repo.getSession(3l));
        assertNull(repo.getSession(4l));

        // tenant is "newTenant"
        try {
            setupSessions(TenantName.from("newTenant"), false);
        } catch (Exception e) {
            fail();
        }
        assertNull(repo.getSession(1l));

        repo.addSession(new SessionHandlerTest.MockSession(1l, FilesApplicationPackage.fromFile(testApp)));
        repo.addSession(new SessionHandlerTest.MockSession(2l, FilesApplicationPackage.fromFile(testApp)));
        assertNotNull(repo.getSession(1l));
        assertNotNull(repo.getSession(2l));
        assertNull(repo.getSession(3l));
    }
}
