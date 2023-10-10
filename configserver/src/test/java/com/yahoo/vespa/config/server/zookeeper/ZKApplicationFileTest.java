// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.zookeeper;

import com.yahoo.config.application.api.ApplicationFile;
import com.yahoo.config.application.api.ApplicationFileTest;
import com.yahoo.path.Path;
import com.yahoo.text.Utf8;
import com.yahoo.vespa.curator.Curator;
import com.yahoo.vespa.curator.mock.MockCurator;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;

import static com.yahoo.vespa.config.server.zookeeper.ZKApplication.USERAPP_ZK_SUBPATH;
import static org.junit.Assert.assertTrue;

/**
 * @author Ulf Lilleengen
 */
public class ZKApplicationFileTest extends ApplicationFileTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private void feed(Curator curator, File dirToFeed) {
        assertTrue(dirToFeed.isDirectory());
        Path appPath = Path.fromString("/0");
        ZKApplicationPackageTest.feedZooKeeper(curator, dirToFeed, appPath.append(USERAPP_ZK_SUBPATH), null, true);
        curator.set(appPath.append(ZKApplicationPackage.fileRegistryNode), Utf8.toBytes("dummyfiles"));
    }

    @Override
    public ApplicationFile getApplicationFile(Path path) throws IOException{
        Curator curator = new MockCurator();
        File tmp = temporaryFolder.newFolder();
        writeAppTo(tmp);
        feed(curator, tmp);
        return new ZKApplicationFile(path, new ZKApplication(curator, Path.fromString("/0")));
    }

}
