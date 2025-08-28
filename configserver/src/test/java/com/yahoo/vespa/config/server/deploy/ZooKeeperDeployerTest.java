// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.deploy;

import com.google.common.collect.ImmutableSet;
import com.yahoo.component.Version;
import com.yahoo.config.application.api.ApplicationMetaData;
import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.config.application.api.FileRegistry;
import com.yahoo.config.model.application.provider.BaseDeployLogger;
import com.yahoo.config.model.application.provider.DeployData;
import com.yahoo.config.model.application.provider.FilesApplicationPackage;
import com.yahoo.config.model.application.provider.MockFileRegistry;
import com.yahoo.config.provision.AllocatedHosts;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.HostSpec;
import com.yahoo.io.IOUtils;
import com.yahoo.path.Path;
import com.yahoo.text.Utf8;
import com.yahoo.vespa.config.server.zookeeper.ZKApplicationPackage;
import com.yahoo.vespa.curator.Curator;
import com.yahoo.vespa.curator.mock.MockCurator;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;

import static com.yahoo.config.model.application.provider.FilesApplicationPackage.fromDir;
import static com.yahoo.config.provision.serialization.AllocatedHostsSerializer.fromJson;
import static com.yahoo.vespa.config.server.session.SessionZooKeeperClient.getSessionPath;
import static com.yahoo.vespa.config.server.zookeeper.ZKApplication.DEFCONFIGS_ZK_SUBPATH;
import static com.yahoo.vespa.config.server.zookeeper.ZKApplication.META_ZK_PATH;
import static com.yahoo.vespa.config.server.zookeeper.ZKApplication.USERAPP_ZK_SUBPATH;
import static com.yahoo.vespa.config.server.zookeeper.ZKApplication.USER_DEFCONFIGS_ZK_SUBPATH;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * @author Ulf Lilleengen
 */
public class ZooKeeperDeployerTest {

    private Curator zk;
    private final Path appPath = Path.fromString("/1");

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();
    private static final String defFile = "test2.def";

    @Before
    public void setupZK() throws IOException {
        zk = new MockCurator();
        ZooKeeperDeployer.Client zkc = new ZooKeeperDeployer.Client(zk, new BaseDeployLogger(), appPath);
        ApplicationPackage app = fromDir(new File("src/test/apps/zkfeed"),
                                         new DeployData(ApplicationId.from("default", "appName", "default"),
                                                        1345L,
                                                        true,
                                                        3L,
                                                        2L),
                                         Map.of());
        Map<Version, FileRegistry> fileRegistries = createFileRegistries();
        app.writeMetaData();
        zkc.initialize();
        zkc.writeApplicationPackage(app);
        zkc.write(fileRegistries);
    }

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
        deploy(FilesApplicationPackage.fromDir(new File("src/test/apps/content"), Map.of()), curator, 1);
        deploy(FilesApplicationPackage.fromDir(new File("src/test/apps/content"), Map.of()), curator, 2);
    }

    private Map<Version, FileRegistry> createFileRegistries() {
        FileRegistry a = new MockFileRegistry();
        a.addFile("fileA");
        FileRegistry b = new MockFileRegistry();
        b.addFile("fileB");
        Map<Version, FileRegistry> registryMap = new HashMap<>();
        registryMap.put(new Version(1, 2, 3), a);
        registryMap.put(new Version(3, 2, 1), b);
        return registryMap;
    }

    @Test
    public void testInitZooKeeper() {
        Curator zk = new MockCurator();
        BaseDeployLogger logger = new BaseDeployLogger();
        long generation = 1L;
        ZooKeeperDeployer.Client client = new ZooKeeperDeployer.Client(zk, logger, Path.fromString("/1"));
        client.initialize();
        Path appPath = Path.fromString("/");
        assertEquals(1, zk.getChildren(appPath).size());
        Path currentAppPath = appPath.append(String.valueOf(generation));
        assertTrue(zk.exists(currentAppPath));
        assertTrue(zk.exists(currentAppPath.append(DEFCONFIGS_ZK_SUBPATH.replaceFirst("/", ""))));
        assertEquals(4, zk.getChildren(currentAppPath).size());
    }

    @Test
    public void testFeedDefFilesToZooKeeper() {
        Path defsPath = appPath.append(DEFCONFIGS_ZK_SUBPATH);
        assertTrue(zk.exists(appPath.append(DEFCONFIGS_ZK_SUBPATH.replaceFirst("/", ""))));
        List<String> children = zk.getChildren(defsPath);
        assertEquals(defsPath + " children", 1, children.size());
        Collections.sort(children);
        assertEquals("a.b.test2", children.get(0));

        assertTrue(zk.exists(appPath.append(USER_DEFCONFIGS_ZK_SUBPATH.replaceFirst("/", ""))));
        Path userDefsPath = appPath.append(USER_DEFCONFIGS_ZK_SUBPATH);
        children = zk.getChildren(userDefsPath);
        assertEquals(1, children.size());
        Collections.sort(children);
        assertEquals("a.b.test2", children.get(0));
    }

    @Test
    public void testFeedAppMetaDataToZooKeeper() {
        assertTrue(zk.exists(appPath.append(META_ZK_PATH)));
        ApplicationMetaData metaData = ApplicationMetaData.fromJsonString(
                Utf8.toString(zk.getData(appPath.append(META_ZK_PATH)).get()));
        assertFalse(metaData.getChecksum().isEmpty());
        assertTrue(metaData.isInternalRedeploy());
        assertEquals(1345, metaData.getDeployTimestamp().longValue());
        assertEquals(3, metaData.getGeneration().longValue());
        assertEquals(2, metaData.getPreviousActiveGeneration());
    }

    @Test
    public void testVersionedFileRegistry() {
        Path fileRegPath = appPath.append(ZKApplicationPackage.fileRegistryNode);
        assertTrue(zk.exists(fileRegPath));
        assertTrue(zk.exists(fileRegPath.append("/1.2.3")));
        assertTrue(zk.exists(fileRegPath.append("/3.2.1")));
        // assertNull("Data at " + fileRegPath, zk.getData(fileRegPath)); Not null any more .. hm
    }

    @Test
    public void include_dirs_are_written_to_ZK() {
        assertTrue(zk.exists(appPath.append(USERAPP_ZK_SUBPATH).append("dir1").append("default.xml")));
        assertTrue(zk.exists(appPath.append(USERAPP_ZK_SUBPATH).append("nested").append("dir2").append("chain2.xml")));
        assertTrue(zk.exists(appPath.append(USERAPP_ZK_SUBPATH).append("nested").append("dir2").append("chain3.xml")));
    }

    @Test
    public void search_chain_dir_written_to_ZK() {
        assertTrue(zk.exists(appPath().append("search").append("chains").append("dir1").append("default.xml")));
        assertTrue(zk.exists(appPath().append("search").append("chains").append("dir2").append("chain2.xml")));
        assertTrue(zk.exists(appPath().append("search").append("chains").append("dir2").append("chain3.xml")));
    }

    private Path appPath() {
        return appPath.append(USERAPP_ZK_SUBPATH);
    }

    @Test
    public void testWritingHostNamesToZooKeeper() throws IOException {
        Curator zk = new MockCurator();
        BaseDeployLogger logger = new BaseDeployLogger();
        Path app = Path.fromString("/1");
        ZooKeeperDeployer.Client client = new ZooKeeperDeployer.Client(zk, logger, app);
        client.initialize();
        HostSpec host1 = new HostSpec("host1.yahoo.com", Optional.empty());
        HostSpec host2 = new HostSpec("host2.yahoo.com", Optional.empty());
        ImmutableSet<HostSpec> hosts = ImmutableSet.of(host1, host2);
        client.write(AllocatedHosts.withHosts(hosts));
        Path hostsPath = app.append(ZKApplicationPackage.allocatedHostsNode);
        assertTrue(zk.exists(hostsPath));

        AllocatedHosts deserialized = fromJson(zk.getData(hostsPath).get());
        assertEquals(hosts, deserialized.getHosts());
    }

    public void deploy(ApplicationPackage applicationPackage, Curator curator, long sessionId) throws IOException {
        ZooKeeperDeployer deployer = new ZooKeeperDeployer(curator, new MockDeployLogger(), applicationPackage.getApplicationId(), sessionId);
        deployer.deploy(applicationPackage, Map.of(new Version(1, 0, 0), new MockFileRegistry()), AllocatedHosts.withHosts(Set.of()));

        Path sessionPath = getSessionPath(applicationPackage.getApplicationId().tenant(), sessionId);
        assertTrue(curator.exists(sessionPath));
    }

    private static class MockDeployLogger implements DeployLogger {
        @Override
        public void log(Level level, String message) { }
    }

}
