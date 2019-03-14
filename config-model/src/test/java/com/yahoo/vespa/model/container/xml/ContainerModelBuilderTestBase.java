// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.xml;

import com.yahoo.component.ComponentId;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.test.MockRoot;
import com.yahoo.container.ComponentsConfig;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.container.ContainerCluster;
import com.yahoo.vespa.model.container.ApplicationContainerCluster;
import com.yahoo.vespa.model.container.ContainerModel;
import com.yahoo.vespa.model.container.component.Component;
import com.yahoo.vespa.model.container.search.ContainerSearch;
import com.yahoo.vespa.model.search.AbstractSearchCluster;
import org.junit.Before;
import org.w3c.dom.Element;

import java.util.Collections;

/**
 * Utility functions for testing the ContainerModelBuilder. Note that XML validation will
 * not be done when using this class
 *
 * @author gjoranv
 */
public abstract class ContainerModelBuilderTestBase {

    public static final String nodesXml =
            "  <nodes>" +
            "    <node hostalias='mockhost' />" +
            "  </nodes>";
    protected MockRoot root;

    public static void createModel(MockRoot root, DeployState deployState, VespaModel vespaModel, Element... containerElems) {
        for (Element containerElem : containerElems) {
            ContainerModel model = new ContainerModelBuilder(false, ContainerModelBuilder.Networking.enable)
                                           .build(deployState, vespaModel, null, root, containerElem);
            ContainerCluster cluster = model.getCluster();
            generateDefaultSearchChains(cluster);
        }
        root.freezeModelTopology();
    }

    public static void createModel(MockRoot root, Element... containerElems) {
        createModel(root, DeployState.createTestState(), null, containerElems);
    }

    private static void generateDefaultSearchChains(ContainerCluster cluster) {
        ContainerSearch search = cluster.getSearch();
        if (search != null)
            search.initializeSearchChains(Collections.<String, AbstractSearchCluster>emptyMap());
    }

    @Before
    public void prepareTest() throws Exception {
        root = new MockRoot("root");
    }

    protected ComponentsConfig componentsConfig() {
        return root.getConfig(ComponentsConfig.class, "default");
    }

    protected ComponentsConfig.Components getComponent(ComponentsConfig componentsConfig, String id) {
        for (ComponentsConfig.Components component : componentsConfig.components()) {
            if (component.id().equals(id))
                return component;
        }
        return null;
    }

    public ApplicationContainerCluster getContainerCluster(String clusterId) {
        return (ApplicationContainerCluster) root.getChildren().get(clusterId);
    }

    public Component<?, ?> getContainerComponent(String clusterId, String componentId) {
        return getContainerCluster(clusterId).getComponentsMap().get(
                ComponentId.fromString(componentId));
    }

}
