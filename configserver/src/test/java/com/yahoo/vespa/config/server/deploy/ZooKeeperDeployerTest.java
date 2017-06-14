// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.deploy;

import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.config.model.application.provider.*;
import com.yahoo.config.provision.Version;
import com.yahoo.io.IOUtils;
import com.yahoo.path.Path;
import com.yahoo.prelude.semantics.parser.ParseException;
import com.yahoo.vespa.curator.mock.MockCurator;
import com.yahoo.vespa.config.server.zookeeper.ConfigCurator;
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
 * @author lulf
 * @since 5.1
 */
public class ZooKeeperDeployerTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();
    private static final String defFile = "test2.def";

    @Test
    public void require_that_deployer_is_initialized() throws IOException, ParseException {
        ConfigCurator zkfacade = ConfigCurator.create(new MockCurator());
        File serverdbDir = folder.newFolder("serverdb");
        File defsDir = new File(serverdbDir, "serverdefs");
        try {
            IOUtils.createWriter(new File(defsDir, defFile), true);
        } catch (IOException e) {
            e.printStackTrace();
            fail();
        }
        deploy(FilesApplicationPackage.fromFile(new File("src/test/apps/content")), zkfacade, Path.fromString("/1"));
        deploy(FilesApplicationPackage.fromFile(new File("src/test/apps/content")), zkfacade, Path.fromString("/2"));
    }

    public void deploy(ApplicationPackage applicationPackage, ConfigCurator configCurator, Path appPath) throws IOException {
        MockDeployLogger logger = new MockDeployLogger();
        ZooKeeperClient client = new ZooKeeperClient(configCurator, logger, true, appPath);
        ZooKeeperDeployer deployer = new ZooKeeperDeployer(client);

        deployer.deploy(applicationPackage, Collections.singletonMap(Version.fromIntValues(1, 0, 0), new MockFileRegistry()), Collections.emptyMap());
        assertTrue(configCurator.exists(appPath.getAbsolute()));
    }

    private static class MockDeployLogger implements DeployLogger {
        @Override
        public void log(Level level, String message) { }
    }
}
