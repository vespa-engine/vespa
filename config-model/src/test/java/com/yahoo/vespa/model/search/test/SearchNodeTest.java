// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.search.test;

import ai.vespa.telemetry.TelemetryConfig;
import com.yahoo.config.model.api.ModelContext;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.deploy.TestProperties;
import com.yahoo.config.model.test.MockRoot;
import com.yahoo.config.provision.zone.ZoneInfo;
import com.yahoo.documentmodel.NewDocumentType;
import com.yahoo.searchlib.TranslogserverConfig;
import com.yahoo.vespa.config.search.core.ProtonConfig;
import com.yahoo.vespa.defaults.Defaults;
import com.yahoo.vespa.model.Host;
import com.yahoo.vespa.model.HostResource;
import com.yahoo.vespa.model.content.ContentSearchCluster;
import com.yahoo.vespa.model.search.NodeSpec;
import com.yahoo.vespa.model.search.SearchNode;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        var deployState = root.getDeployState();
        var applicationId = deployState.getProperties().applicationId();
        var zone = ZoneInfo.from(deployState.zone());
        var documentType = new NewDocumentType(new NewDocumentType.Name("book"));
        var cluster = new ContentSearchCluster(root, "mycluster", deployState.getProperties().featureFlags(),
            Map.of("book", documentType), Set.of(), true, syncTransactionLog, 0, applicationId, zone);
        return SearchNode.create(cluster, name, distributionKey, nodeSpec, "mycluster", null, flushOnShutDown,
            null, isHosted, syncTransactionLog, "");
    }

    private static SearchNode createSearchNode(MockRoot root, Boolean syncTransactionLog) {
        return createSearchNode(root, "mynode", 3, new NodeSpec(7, 5), true, true, syncTransactionLog);
    }

    private static SearchNode createSearchNode(MockRoot root) {
        return createSearchNode(root, true);
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

    @Test
    void requireThatTelemetryCanBeEnabled() {
        assertFalse(getTelemetryConfig(new TestProperties()).enabled());
        var telemetryConfig = getTelemetryConfig(new TestProperties().opentelemetrySdk(new TestProperties.OpenTelemetrySettings(true, 2.0)));
        assertTrue(telemetryConfig.enabled());
        assertEquals(2.0, telemetryConfig.samplingRatio());
        assertEquals(Map.of("application", "default",
                            "cloud", "default",
                            "cluster.id", "mycluster",
                            "cluster.type", "search",
                            "environment", "prod",
                            "service.name", "default.default.mycluster",
                            "tenant", "default",
                            "zone", "prod.default"),
                     telemetryConfig.resourceAttribute());
        assertFalse(getTelemetryConfig(new TestProperties().opentelemetrySdk(new TestProperties.OpenTelemetrySettings(false, 3.0))).enabled());
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

    private TelemetryConfig getTelemetryConfig(ModelContext.Properties properties) {
        MockRoot root = createRoot(properties);
        SearchNode node = createSearchNode(root, false);
        prepare(root, node);
        var builder = new TelemetryConfig.Builder();
        node.cascadeConfig(builder);
        return builder.build();
    }

}
