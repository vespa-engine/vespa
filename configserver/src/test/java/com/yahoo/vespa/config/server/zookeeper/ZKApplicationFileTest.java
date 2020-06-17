// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.zookeeper;

import com.yahoo.config.application.api.ApplicationFile;
import com.yahoo.config.application.api.ApplicationFileTest;
import com.yahoo.path.Path;
import com.yahoo.vespa.curator.mock.MockCurator;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;

import static org.junit.Assert.assertTrue;

/**
 * @author Ulf Lilleengen
 */
public class ZKApplicationFileTest extends ApplicationFileTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private void feed(ConfigCurator zk, File dirToFeed) {
        assertTrue(dirToFeed.isDirectory());
        String appPath = "/0";
        ZKApplicationPackageTest.feedZooKeeper(zk, dirToFeed, appPath + ConfigCurator.USERAPP_ZK_SUBPATH, null, true);
        zk.putData(appPath, ZKApplicationPackage.fileRegistryNode, "dummyfiles");
    }

    @Override
    public ApplicationFile getApplicationFile(Path path) throws IOException{
        ConfigCurator configCurator = ConfigCurator.create(new MockCurator());
        File tmp = temporaryFolder.newFolder();
        writeAppTo(tmp);
        feed(configCurator, tmp);
        return new ZKApplicationFile(path, new ZKApplication(configCurator, Path.fromString("/0")));
    }

}
