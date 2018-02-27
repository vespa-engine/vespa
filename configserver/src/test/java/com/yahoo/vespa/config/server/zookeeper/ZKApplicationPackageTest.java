// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.zookeeper;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.util.Collections;
import java.util.Optional;
import java.util.regex.Pattern;

import com.yahoo.config.application.api.DeploymentSpec;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.provision.Flavor;
import com.yahoo.config.provision.HostSpec;
import com.yahoo.config.provision.NodeFlavors;
import com.yahoo.config.provision.AllocatedHosts;
import com.yahoo.config.provision.Version;
import com.yahoo.config.provisioning.FlavorsConfig;
import com.yahoo.path.Path;
import com.yahoo.text.Utf8;
import com.yahoo.vespa.config.server.TestWithCurator;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.yahoo.io.IOUtils;

public class ZKApplicationPackageTest extends TestWithCurator {

    private static final String APP = "src/test/apps/zkapp";
    private static final String TEST_FLAVOR_NAME = "test-flavor";
    private static final Optional<Flavor> TEST_FLAVOR = new MockNodeFlavors().getFlavor(TEST_FLAVOR_NAME);
    private static final AllocatedHosts ALLOCATED_HOSTS = AllocatedHosts.withHosts(
            Collections.singleton(new HostSpec("foo.yahoo.com", Collections.emptyList(), TEST_FLAVOR, Optional.empty())));

    @Rule
    public TemporaryFolder tmpDir = new TemporaryFolder();

    @Test
    public void testBasicZKFeed() throws IOException {
        feed(configCurator, new File(APP));
        ZKApplicationPackage zkApp = new ZKApplicationPackage(configCurator, Path.fromString("/0"), Optional.of(new MockNodeFlavors()));
        assertTrue(Pattern.compile(".*<slobroks>.*",Pattern.MULTILINE+Pattern.DOTALL).matcher(IOUtils.readAll(zkApp.getServices())).matches());
        assertTrue(Pattern.compile(".*<alias>.*",Pattern.MULTILINE+Pattern.DOTALL).matcher(IOUtils.readAll(zkApp.getHosts())).matches());
        assertTrue(Pattern.compile(".*<slobroks>.*",Pattern.MULTILINE+Pattern.DOTALL).matcher(IOUtils.readAll(zkApp.getFile(Path.fromString("services.xml")).createReader())).matches());
        DeployState deployState = new DeployState.Builder().applicationPackage(zkApp).build(true);
        assertEquals(deployState.getSearchDefinitions().size(), 5);
        assertEquals(zkApp.searchDefinitionContents().size(), 5);
        assertEquals(IOUtils.readAll(zkApp.getRankingExpression("foo.expression")), "foo()+1\n");
        assertEquals(zkApp.getFiles(Path.fromString(""), "xml").size(), 3);
        assertEquals(zkApp.getFileReference(Path.fromString("components/file.txt")).getAbsolutePath(), "/home/vespa/test/file.txt");
        try (Reader foo = zkApp.getFile(Path.fromString("files/foo.json")).createReader()) {
            assertEquals(IOUtils.readAll(foo), "foo : foo\n");
        }
        try (Reader bar = zkApp.getFile(Path.fromString("files/sub/bar.json")).createReader()) {
            assertEquals(IOUtils.readAll(bar), "bar : bar\n");
        }
        assertTrue(zkApp.getFile(Path.createRoot()).exists());
        assertTrue(zkApp.getFile(Path.createRoot()).isDirectory());
        Version goodVersion = Version.fromIntValues(3, 0, 0);
        assertTrue(zkApp.getFileRegistryMap().containsKey(goodVersion));
        assertFalse(zkApp.getFileRegistryMap().containsKey(Version.fromIntValues(0, 0, 0)));
        assertThat(zkApp.getFileRegistryMap().get(goodVersion).fileSourceHost(), is("dummyfiles"));
        AllocatedHosts readInfo = zkApp.getAllocatedHosts().get();
        assertThat(Utf8.toString(readInfo.toJson()), is(Utf8.toString(ALLOCATED_HOSTS.toJson())));
        assertThat(readInfo.getHosts().iterator().next().flavor(), is(TEST_FLAVOR));
        assertTrue(zkApp.getDeployment().isPresent());
        assertThat(DeploymentSpec.fromXml(zkApp.getDeployment().get()).globalServiceId().get(), is("mydisc"));
    }

    private void feed(ConfigCurator zk, File dirToFeed) throws IOException {
        assertTrue(dirToFeed.isDirectory());
        zk.feedZooKeeper(dirToFeed, "/0" + ConfigCurator.USERAPP_ZK_SUBPATH, null, true);
        String metaData = "{\"deploy\":{\"user\":\"foo\",\"from\":\"bar\",\"timestamp\":1},\"application\":{\"name\":\"foo\",\"checksum\":\"abc\",\"generation\":4,\"previousActiveGeneration\":3}}";
        zk.putData("/0", ConfigCurator.META_ZK_PATH, metaData);
        zk.putData("/0/" + ZKApplicationPackage.fileRegistryNode + "/3.0.0", "dummyfiles");
        zk.putData("/0/" + ZKApplicationPackage.allocatedHostsNode, ALLOCATED_HOSTS.toJson());
    }

    private static class MockNodeFlavors extends NodeFlavors{

        MockNodeFlavors() { super(flavorsConfig()); }

        private static FlavorsConfig flavorsConfig() {
            return new FlavorsConfig(new FlavorsConfig.Builder()
                            .flavor(new FlavorsConfig.Flavor.Builder().name(TEST_FLAVOR_NAME))
            );
        }
    }
}
