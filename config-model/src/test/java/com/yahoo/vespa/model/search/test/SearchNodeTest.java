// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for search node.
 *
 * @author Geir Storli
 */
public class SearchNodeTest {

    private void assertBaseDir(String expected, SearchNode node) {
        ProtonConfig.Builder builder = new ProtonConfig.Builder();
        node.getConfig(builder);
        ProtonConfig cfg = new ProtonConfig(builder);
        assertEquals(expected, cfg.basedir());
    }

    private void prepare(MockRoot root, SearchNode node) {
        Host host = new Host(root, "mockhost");
        node.setHostResource(new HostResource(host));
        node.setBasePort(200);
        node.initService(root.getDeployState());
        root.freezeModelTopology();
    }

    private static SearchNode createSearchNode(MockRoot root, String name, int distributionKey, NodeSpec nodeSpec,
                                               boolean flushOnShutDown, boolean isHosted,
                                               Boolean syncTransactionLog) {
        return SearchNode.create(root, name, distributionKey, nodeSpec, "mycluster", null, flushOnShutDown,
                null, isHosted, syncTransactionLog, "");
    }

    private static SearchNode createSearchNode(MockRoot root, Boolean syncTransactionLog) {
        return createSearchNode(root, "mynode", 3, new NodeSpec(7, 5), true, true, syncTransactionLog);
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
        SearchNode node = createSearchNode(root, "mynode", 3, new NodeSpec(7, 5), false,
                                           root.getDeployState().isHosted(), true);
        prepare(root, node);
        assertBaseDir(Defaults.getDefaults().underVespaHome("var/db/vespa/search/cluster.mycluster/n3"), node);
    }

    @Test
    void requireThatPreShutdownCommandIsEmptyWhenNotActivated() {
        MockRoot root = new MockRoot("");
        SearchNode node = createSearchNode(root, "mynode", 3, new NodeSpec(7, 5), false,
                                           root.getDeployState().isHosted(), true);
        node.setHostResource(new HostResource(new Host(node, "mynbode")));
        node.initService(root.getDeployState());
        assertFalse(node.getPreShutdownCommand().isPresent());
    }

    @Test
    void requireThatPreShutdownCommandUsesPrepareRestartWhenActivated() {
        MockRoot root = new MockRoot("");
        SearchNode node = createSearchNode(root, "mynode2", 4, new NodeSpec(7, 5), true,
                                           root.getDeployState().isHosted(), true);
        node.setHostResource(new HostResource(new Host(node, "mynbode2")));
        node.initService(root.getDeployState());
        assertTrue(node.getPreShutdownCommand().isPresent());
        assertTrue(node.getPreShutdownCommand().get().contains("vespa-proton-cmd " + node.getRpcPort() + " prepareRestart"));
    }

    private MockRoot createRoot(ModelContext.Properties properties) {
        return new MockRoot("", new DeployState.Builder().properties(properties).build());
    }

    private TranslogserverConfig getTlsConfig(ModelContext.Properties properties, Boolean syncTransactionLog) {
        MockRoot root = createRoot(properties);
        SearchNode node = createSearchNode(root, syncTransactionLog);
        prepare(root, node);
        TranslogserverConfig.Builder tlsBuilder = new TranslogserverConfig.Builder();
        node.getConfig(tlsBuilder);
        return tlsBuilder.build();
    }

}
