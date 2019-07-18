// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.session;

import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.config.model.application.provider.FilesApplicationPackage;
import com.yahoo.test.ManualClock;
import com.yahoo.config.provision.TenantName;
import com.yahoo.vespa.config.server.GlobalComponentRegistry;
import com.yahoo.vespa.config.server.MockReloadHandler;
import com.yahoo.vespa.config.server.TestComponentRegistry;
import com.yahoo.vespa.config.server.application.TenantApplications;
import com.yahoo.io.IOUtils;
import com.yahoo.vespa.config.server.host.HostRegistry;
import com.yahoo.vespa.config.server.http.SessionHandlerTest;

import com.yahoo.vespa.curator.mock.MockCurator;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
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
        File configserverDbDir = temporaryFolder.newFolder().getAbsoluteFile();
        if (createInitialSessions) {
            Path sessionsPath = Paths.get(configserverDbDir.getAbsolutePath(), "tenants", tenantName.value(), "sessions");
            IOUtils.copyDirectory(testApp, sessionsPath.resolve("1").toFile());
            IOUtils.copyDirectory(testApp, sessionsPath.resolve("2").toFile());
            IOUtils.copyDirectory(testApp, sessionsPath.resolve("3").toFile());
        }
        clock = new ManualClock(Instant.ofEpochSecond(1));
        GlobalComponentRegistry globalComponentRegistry = new TestComponentRegistry.Builder()
                .curator(new MockCurator())
                .clock(clock)
                .configServerConfig(new ConfigserverConfig.Builder()
                                            .configServerDBDir(configserverDbDir.getAbsolutePath())
                                            .configDefinitionsDir(temporaryFolder.newFolder().getAbsolutePath())
                                            .sessionLifetime(5)
                                            .build())
                .build();
        LocalSessionLoader loader = new SessionFactoryImpl(globalComponentRegistry,
                                                           TenantApplications.create(globalComponentRegistry, new MockReloadHandler(), tenantName),
                                                           new HostRegistry<>(),
                                                           tenantName);
        repo = new LocalSessionRepo(tenantName, globalComponentRegistry, loader);
    }

    @Test
    public void require_that_sessions_can_be_loaded_from_disk() {
        assertNotNull(repo.getSession(1L));
        assertNotNull(repo.getSession(2L));
        assertNotNull(repo.getSession(3L));
        assertNull(repo.getSession(4L));
    }

    @Test
    public void require_that_old_sessions_are_purged() {
        clock.advance(Duration.ofSeconds(1));
        assertNotNull(repo.getSession(1L));
        assertNotNull(repo.getSession(2L));
        assertNotNull(repo.getSession(3L));
        clock.advance(Duration.ofSeconds(1));
        assertNotNull(repo.getSession(1L));
        assertNotNull(repo.getSession(2L));
        assertNotNull(repo.getSession(3L));
        clock.advance(Duration.ofSeconds(1));
        addSession(4L, 6);
        assertNotNull(repo.getSession(1L));
        assertNotNull(repo.getSession(2L));
        assertNotNull(repo.getSession(3L));
        assertNotNull(repo.getSession(4L));
        clock.advance(Duration.ofSeconds(1));
        addSession(5L, 10);
        repo.purgeOldSessions();
        assertNull(repo.getSession(1L));
        assertNull(repo.getSession(2L));
        assertNull(repo.getSession(3L));
    }

    @Test
    public void require_that_all_sessions_are_deleted() {
        repo.close();
        assertNull(repo.getSession(1L));
        assertNull(repo.getSession(2L));
        assertNull(repo.getSession(3L));
    }

    private void addSession(long sessionId, long createTime) {
        repo.addSession(new SessionHandlerTest.MockSession(sessionId, FilesApplicationPackage.fromFile(testApp), createTime));
    }

    @Test
    public void require_that_sessions_belong_to_a_tenant() {
        // tenant is "default"
        assertNotNull(repo.getSession(1L));
        assertNotNull(repo.getSession(2L));
        assertNotNull(repo.getSession(3L));
        assertNull(repo.getSession(4L));

        // tenant is "newTenant"
        try {
            setupSessions(TenantName.from("newTenant"), false);
        } catch (Exception e) {
            fail();
        }
        assertNull(repo.getSession(1L));

        repo.addSession(new SessionHandlerTest.MockSession(1L, FilesApplicationPackage.fromFile(testApp)));
        repo.addSession(new SessionHandlerTest.MockSession(2L, FilesApplicationPackage.fromFile(testApp)));
        assertNotNull(repo.getSession(1L));
        assertNotNull(repo.getSession(2L));
        assertNull(repo.getSession(3L));
    }
}
