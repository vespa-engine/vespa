// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.deploy;

import com.google.common.collect.ImmutableSet;
import com.yahoo.component.Version;
import com.yahoo.config.application.api.ApplicationMetaData;
import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.application.api.FileRegistry;
import com.yahoo.config.model.application.provider.BaseDeployLogger;
import com.yahoo.config.model.application.provider.DeployData;
import com.yahoo.config.model.application.provider.FilesApplicationPackage;
import com.yahoo.config.model.application.provider.MockFileRegistry;
import com.yahoo.config.provision.AllocatedHosts;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.HostSpec;
import com.yahoo.config.provision.Tags;
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

import static com.yahoo.config.provision.serialization.AllocatedHostsSerializer.fromJson;
import static com.yahoo.vespa.config.server.zookeeper.ZKApplication.DEFCONFIGS_ZK_SUBPATH;
import static com.yahoo.vespa.config.server.zookeeper.ZKApplication.META_ZK_PATH;
import static com.yahoo.vespa.config.server.zookeeper.ZKApplication.USERAPP_ZK_SUBPATH;
import static com.yahoo.vespa.config.server.zookeeper.ZKApplication.USER_DEFCONFIGS_ZK_SUBPATH;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for ZooKeeperClient.
 *
 * @author hmusum
 */
public class ZooKeeperClientTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private Curator zk;
    private final Path appPath = Path.fromString("/1");

    @Before
    public void setupZK() throws IOException {
        zk = new MockCurator();
        ZooKeeperClient zkc = new ZooKeeperClient(zk, new BaseDeployLogger(), appPath);
        ApplicationPackage app = FilesApplicationPackage.fromFileWithDeployData(new File("src/test/apps/zkfeed"),
                                                                                new DeployData("/bar/baz",
                                                                                               ApplicationId.from("default", "appName", "default"),
                                                                                               Tags.fromString("tag1 tag2"),
                                                                                               1345L,
                                                                                               true,
                                                                                               3L,
                                                                                               2L));
        Map<Version, FileRegistry> fileRegistries = createFileRegistries();
        app.writeMetaData();
        zkc.initialize();
        zkc.writeApplicationPackage(app);
        zkc.write(fileRegistries);
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
        ZooKeeperClient zooKeeperClient = new ZooKeeperClient(zk, logger, Path.fromString("/1"));
        zooKeeperClient.initialize();
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
        assertTrue(metaData.getChecksum().length() > 0);
        assertTrue(metaData.isInternalRedeploy());
        assertEquals("/bar/baz", metaData.getDeployPath());
        assertEquals(Tags.fromString("tag1 tag2"), metaData.getTags());
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
        ZooKeeperClient zooKeeperClient = new ZooKeeperClient(zk, logger, app);
        zooKeeperClient.initialize();
        HostSpec host1 = new HostSpec("host1.yahoo.com", Collections.emptyList(), Optional.empty());
        HostSpec host2 = new HostSpec("host2.yahoo.com", Collections.emptyList(), Optional.empty());
        ImmutableSet<HostSpec> hosts = ImmutableSet.of(host1, host2);
        zooKeeperClient.write(AllocatedHosts.withHosts(hosts));
        Path hostsPath = app.append(ZKApplicationPackage.allocatedHostsNode);
        assertTrue(zk.exists(hostsPath));
        
        AllocatedHosts deserialized = fromJson(zk.getData(hostsPath).get());
        assertEquals(hosts, deserialized.getHosts());
    }

}
