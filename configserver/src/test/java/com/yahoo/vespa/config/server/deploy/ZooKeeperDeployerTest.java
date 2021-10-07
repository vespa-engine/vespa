// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.deploy;

import com.yahoo.component.Version;
import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.config.model.application.provider.FilesApplicationPackage;
import com.yahoo.config.model.application.provider.MockFileRegistry;
import com.yahoo.config.provision.AllocatedHosts;
import com.yahoo.io.IOUtils;
import com.yahoo.path.Path;
import com.yahoo.vespa.curator.Curator;
import com.yahoo.vespa.curator.mock.MockCurator;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.logging.Level;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * @author Ulf Lilleengen
 */
public class ZooKeeperDeployerTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();
    private static final String defFile = "test2.def";

    @Test
    public void require_that_deployer_is_initialized() throws IOException {
        Curator curator = new MockCurator();
        File serverdbDir = folder.newFolder("serverdb");
        File defsDir = new File(serverdbDir, "serverdefs");
        try {
            IOUtils.createWriter(new File(defsDir, defFile), true);
        } catch (IOException e) {
            e.printStackTrace();
            fail();
        }
        deploy(FilesApplicationPackage.fromFile(new File("src/test/apps/content")), curator, Path.fromString("/1"));
        deploy(FilesApplicationPackage.fromFile(new File("src/test/apps/content")), curator, Path.fromString("/2"));
    }

    public void deploy(ApplicationPackage applicationPackage, Curator curator, Path appPath) throws IOException {
        MockDeployLogger logger = new MockDeployLogger();
        ZooKeeperClient client = new ZooKeeperClient(curator, logger, appPath);
        ZooKeeperDeployer deployer = new ZooKeeperDeployer(client);

        deployer.deploy(applicationPackage, Collections.singletonMap(new Version(1, 0, 0), new MockFileRegistry()), AllocatedHosts.withHosts(Collections.emptySet()));
        assertTrue(curator.exists(appPath));
    }

    private static class MockDeployLogger implements DeployLogger {
        @Override
        public void log(Level level, String message) { }
    }

}
