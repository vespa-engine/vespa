// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.search.test;

import com.yahoo.config.model.api.ModelContext;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.deploy.TestProperties;
import com.yahoo.config.model.producer.AbstractConfigProducer;
import com.yahoo.config.model.test.MockRoot;
import com.yahoo.searchlib.TranslogserverConfig;
import com.yahoo.vespa.config.search.core.ProtonConfig;
import com.yahoo.vespa.defaults.Defaults;
import com.yahoo.vespa.model.Host;
import com.yahoo.vespa.model.HostResource;
import com.yahoo.vespa.model.search.NodeSpec;
import com.yahoo.vespa.model.search.SearchNode;
import com.yahoo.vespa.model.search.TransactionLogServer;
import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.junit.Test;

import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

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

    private void prepare(MockRoot root, SearchNode node) {
        Host host = new Host(root, "mockhost");
        TransactionLogServer tls = new TransactionLogServer(root, "mycluster");
        tls.setHostResource(new HostResource(host));
        tls.setBasePort(100);
        tls.initService(root.deployLogger());
        node.setTls(tls);
        node.setHostResource(new HostResource(host));
        node.setBasePort(200);
        node.initService(root.deployLogger());
        root.freezeModelTopology();
    }

    private static SearchNode createSearchNode(MockRoot root, String name, int distributionKey,
                                               NodeSpec nodeSpec, boolean flushOnShutDown, boolean isHosted, boolean combined) {
        return SearchNode.create(root, name, distributionKey, nodeSpec, "mycluster", null, flushOnShutDown, Optional.empty(), Optional.empty(), isHosted, combined);
    }

    private static SearchNode createSearchNode(MockRoot root) {
        return createSearchNode(root, "mynode", 3, new NodeSpec(7, 5), true, true, false);
    }

    @Test
    public void requireThatBasedirIsCorrectForElasticMode() {
        MockRoot root = new MockRoot("");
        SearchNode node = createSearchNode(root, "mynode", 3, new NodeSpec(7, 5), false, root.getDeployState().isHosted(), false);
        prepare(root, node);
        assertBaseDir(Defaults.getDefaults().underVespaHome("var/db/vespa/search/cluster.mycluster/n3"), node);
    }

    @Test
    public void requireThatPreShutdownCommandIsEmptyWhenNotActivated() {
        MockRoot root = new MockRoot("");
        SearchNode node = createSearchNode(root, "mynode", 3, new NodeSpec(7, 5), false, root.getDeployState().isHosted(), false);
        node.setHostResource(new HostResource(new Host(node, "mynbode")));
        node.initService(root.deployLogger());
        assertFalse(node.getPreShutdownCommand().isPresent());
    }

    @Test
    public void requireThatPreShutdownCommandUsesPrepareRestartWhenActivated() {
        MockRoot root = new MockRoot("");
        SearchNode node = createSearchNode(root, "mynode2", 4, new NodeSpec(7, 5), true, root.getDeployState().isHosted(), false);
        node.setHostResource(new HostResource(new Host(node, "mynbode2")));
        node.initService(root.deployLogger());
        assertTrue(node.getPreShutdownCommand().isPresent());
        Assert.assertThat(node.getPreShutdownCommand().get(),
                CoreMatchers.containsString("vespa-proton-cmd " + node.getRpcPort() + " prepareRestart"));
    }

    private MockRoot createRoot(ModelContext.Properties properties) {
        return new MockRoot("", new DeployState.Builder().properties(properties).build());
    }

    private TranslogserverConfig getTlsConfig(ModelContext.Properties properties) {
        MockRoot root = createRoot(properties);
        SearchNode node = createSearchNode(root);
        prepare(root, node);
        TranslogserverConfig.Builder tlsBuilder = new TranslogserverConfig.Builder();
        node.getConfig(tlsBuilder);
        return tlsBuilder.build();
    }

}
