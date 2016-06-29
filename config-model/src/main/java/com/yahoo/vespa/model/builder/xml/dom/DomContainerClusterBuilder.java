// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.builder.xml.dom;

import com.yahoo.text.XML;
import com.yahoo.config.model.producer.AbstractConfigProducer;
import com.yahoo.vespa.model.container.xml.ContainerModelBuilder;
import com.yahoo.vespa.model.container.ContainerCluster;
import com.yahoo.vespa.model.container.component.Component;
import com.yahoo.vespa.model.container.component.Handler;
import com.yahoo.vespa.model.container.component.chain.ProcessingHandler;
import com.yahoo.vespa.model.container.search.searchchain.SearchChains;
import org.w3c.dom.Element;

import java.util.ArrayList;
import java.util.List;

/**
 * @author gjoranv
 */
public abstract class DomContainerClusterBuilder<CLUSTER extends ContainerCluster>
        extends VespaDomBuilder.DomConfigProducerBuilder<CLUSTER> {

    protected final Element outerChainsElem;

    public DomContainerClusterBuilder(Element outerChainsElem) {
        this.outerChainsElem = outerChainsElem;
    }

    protected void buildAndAddUserConfiguredComponents(ContainerCluster cluster, Element spec) {
        buildAndAddConfiguredHandlers(cluster, spec);
        buildAndAddClientProviders(cluster, spec);
        buildAndAddServerProviders(cluster, spec);
        buildAndAddGenericComponents(cluster, spec);
        buildAndAddRenderers(cluster, spec);
        buildAndAddFilters(cluster, spec);
    }

    public void addSpecialHandlers(ContainerCluster cluster) {
        ContainerModelBuilder.addDefaultHandler_legacyBuilder(cluster);
    }

    private void buildAndAddConfiguredHandlers(ContainerCluster cluster, Element spec) {
        List<Handler> handlers = buildConfiguredHandlers(new DomHandlerBuilder(true), cluster, spec, "handler");

        for (Handler handler : handlers) {
            // TODO: hack to avoid adding a simple Handler for an explicitly declared SearchHandler
            if (handler.getClassId().getName().equals("com.yahoo.search.handler.SearchHandler")) {
                final ProcessingHandler<SearchChains> searchHandler = new ProcessingHandler<>(
                        cluster.getSearch().getChains(), "com.yahoo.search.handler.SearchHandler");
                searchHandler.addServerBindings("http://*/search/*");
                cluster.addComponent(searchHandler);
            } else
                cluster.addComponent(handler);
        }
    }

    private void buildAndAddClientProviders(ContainerCluster cluster, Element spec) {
        List<Handler> clients = buildConfiguredHandlers(new DomClientProviderBuilder(), cluster, spec, "client");

        for (Handler client : clients) {
            cluster.addComponent(client);
        }
    }

    private void buildAndAddServerProviders(ContainerCluster cluster, Element spec) {
        ContainerModelBuilder.addConfiguredComponents(cluster, spec, "server");
    }

    private void buildAndAddGenericComponents(ContainerCluster cluster, Element spec) {
        ContainerModelBuilder.addConfiguredComponents(cluster, spec, DomComponentBuilder.elementName);
    }

    private void buildAndAddFilters(ContainerCluster cluster, Element spec) {
        for (Component component : buildConfiguredFilters(cluster, spec, "filter")) {
            cluster.addComponent(component);
        }
    }

    private List<Component> buildConfiguredFilters(AbstractConfigProducer ancestor,
                                                   Element spec,
                                                   String componentName) {
        List<Component> components = new ArrayList<>();

        for (Element node : XML.getChildren(spec, componentName)) {
            components.add(new DomFilterBuilder().build(ancestor, node));
        }
        return components;
    }

    private List<Handler> buildConfiguredHandlers(DomHandlerBuilder builder,
                                                  AbstractConfigProducer ancestor,
                                                  Element spec,
                                                  String componentName) {
        List<Handler> handlers = new ArrayList<>();

        for (Element node : XML.getChildren(spec, componentName)) {
            handlers.add(builder.build(ancestor, node));
        }
        return handlers;
    }

    protected void buildAndAddRenderers(ContainerCluster cluster, Element spec) {
        ContainerModelBuilder.addConfiguredComponents(cluster, spec, "renderer");
    }

    protected void buildAndAddProcessingRenderers(ContainerCluster cluster, Element spec) {
        ContainerModelBuilder.addConfiguredComponents(cluster, spec, "renderer");
    }

}
