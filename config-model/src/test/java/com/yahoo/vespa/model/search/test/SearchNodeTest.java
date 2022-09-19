// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.search.test;

import com.yahoo.config.model.api.ModelContext;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.deploy.TestProperties;
import com.yahoo.config.model.test.MockRoot;
import com.yahoo.searchlib.TranslogserverConfig;
import com.yahoo.vespa.config.search.core.ProtonConfig;
import com.yahoo.vespa.defaults.Defaults;
import com.yahoo.vespa.model.Host;
import com.yahoo.vespa.model.HostResource;
import com.yahoo.vespa.model.search.NodeSpec;
import com.yahoo.vespa.model.search.SearchNode;
import com.yahoo.vespa.model.search.TransactionLogServer;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for search node.
 *
 * @author geirst
 */
public class SearchNodeTest {

    private void assertBaseDir(String expected, SearchNode node) {
        ProtonConfig.Builder builder = new ProtonConfig.Builder();
        node.getConfig(builder);
        ProtonConfig cfg = new ProtonConfig(builder);
        assertEquals(expected, cfg.basedir());
    }

    private void prepare(MockRoot root, SearchNode node, Boolean useFsync) {
        Host host = new Host(root, "mockhost");
        TransactionLogServer tls = new TransactionLogServer(root, "mycluster", useFsync);
        tls.setHostResource(new HostResource(host));
        tls.setBasePort(100);
        tls.initService(root.getDeployState());
        node.setTls(tls);
        node.setHostResource(new HostResource(host));
        node.setBasePort(200);
        node.initService(root.getDeployState());
        root.freezeModelTopology();
    }

    private static SearchNode createSearchNode(MockRoot root, String name, int distributionKey, NodeSpec nodeSpec,
                                               boolean flushOnShutDown, boolean isHosted, ModelContext.FeatureFlags featureFlags) {
        return SearchNode.create(root, name, distributionKey, nodeSpec, "mycluster", null, flushOnShutDown,
                Optional.empty(), Optional.empty(), isHosted, 0.0, featureFlags);
    }

    private static SearchNode createSearchNode(MockRoot root) {
        return createSearchNode(root, "mynode", 3, new NodeSpec(7, 5), true, true, new TestProperties());
    }

    @Test
    void requireThatSyncIsHonoured() {
        assertTrue(getTlsConfig(new TestProperties(), null).usefsync());
        assertTrue(getTlsConfig(new TestProperties(), true).usefsync());
        assertFalse(getTlsConfig(new TestProperties(), false).usefsync());
    }

    @Test
    void requireThatBasedirIsCorrectForElasticMode() {
        MockRoot root = new MockRoot("");
        SearchNode node = createSearchNode(root, "mynode", 3, new NodeSpec(7, 5), false, root.getDeployState().isHosted(), new TestProperties());
        prepare(root, node, true);
        assertBaseDir(Defaults.getDefaults().underVespaHome("var/db/vespa/search/cluster.mycluster/n3"), node);
    }

    @Test
    void requireThatPreShutdownCommandIsEmptyWhenNotActivated() {
        MockRoot root = new MockRoot("");
        SearchNode node = createSearchNode(root, "mynode", 3, new NodeSpec(7, 5), false, root.getDeployState().isHosted(), new TestProperties());
        node.setHostResource(new HostResource(new Host(node, "mynbode")));
        node.initService(root.getDeployState());
        assertFalse(node.getPreShutdownCommand().isPresent());
    }

    @Test
    void requireThatPreShutdownCommandUsesPrepareRestartWhenActivated() {
        MockRoot root = new MockRoot("");
        SearchNode node = createSearchNode(root, "mynode2", 4, new NodeSpec(7, 5), true, root.getDeployState().isHosted(), new TestProperties());
        node.setHostResource(new HostResource(new Host(node, "mynbode2")));
        node.initService(root.getDeployState());
        assertTrue(node.getPreShutdownCommand().isPresent());
        assertTrue(node.getPreShutdownCommand().get().contains("vespa-proton-cmd " + node.getRpcPort() + " prepareRestart"));
    }

    private void verifyCodePlacement(boolean hugePages) {
        MockRoot root = new MockRoot("");
        SearchNode node = createSearchNode(root, "mynode2", 4, new NodeSpec(7, 5), true, false, new TestProperties().loadCodeAsHugePages(hugePages));
        node.setHostResource(new HostResource(new Host(node, "mynbode2")));
        node.initService(root.getDeployState());
        assertEquals(hugePages, node.getEnvVars().get("VESPA_LOAD_CODE_AS_HUGEPAGES") != null);
    }

    @Test
    void requireThatCodePageTypeCanBeControlled() {
        verifyCodePlacement(true);
        verifyCodePlacement(false);
    }

    private void verifySharedStringRepoReclaim(boolean sharedStringRepoNoReclaim) {
        MockRoot root = new MockRoot("");
        SearchNode node = createSearchNode(root, "mynode2", 4, new NodeSpec(7, 5), true, false, new TestProperties().sharedStringRepoNoReclaim(sharedStringRepoNoReclaim));
        node.setHostResource(new HostResource(new Host(node, "mynbode2")));
        node.initService(root.getDeployState());
        assertEquals(sharedStringRepoNoReclaim, node.getEnvVars().get("VESPA_SHARED_STRING_REPO_NO_RECLAIM") != null);
    }

    @Test
    void requireThatSharedRepoReclaimCanBeControlled() {
        verifySharedStringRepoReclaim(true);
        verifySharedStringRepoReclaim(false);
    }

    private MockRoot createRoot(ModelContext.Properties properties) {
        return new MockRoot("", new DeployState.Builder().properties(properties).build());
    }

    private TranslogserverConfig getTlsConfig(ModelContext.Properties properties, Boolean useFsync) {
        MockRoot root = createRoot(properties);
        SearchNode node = createSearchNode(root);
        prepare(root, node, useFsync);
        TranslogserverConfig.Builder tlsBuilder = new TranslogserverConfig.Builder();
        node.getConfig(tlsBuilder);
        return tlsBuilder.build();
    }

}
