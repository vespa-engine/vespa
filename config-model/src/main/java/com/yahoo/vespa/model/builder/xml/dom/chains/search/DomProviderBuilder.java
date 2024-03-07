// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.builder.xml.dom.chains.search;

import com.yahoo.component.chain.model.ChainSpecification;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.producer.AnyConfigProducer;
import com.yahoo.config.model.producer.TreeConfigProducer;
import com.yahoo.search.searchchain.model.federation.FederationOptions;
import com.yahoo.search.searchchain.model.federation.LocalProviderSpec;
import com.yahoo.text.XML;
import com.yahoo.vespa.model.builder.xml.dom.chains.ComponentsBuilder;
import com.yahoo.vespa.model.container.search.searchchain.GenericProvider;
import com.yahoo.vespa.model.container.search.searchchain.LocalProvider;
import com.yahoo.vespa.model.container.search.searchchain.Provider;
import com.yahoo.vespa.model.container.search.searchchain.Source;
import org.w3c.dom.Element;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Builds a provider from xml.
 * The demangling of provider types is taken care of here,
 * since the mangling is an intrinsic of the configuration language,
 * not the model itself.
 *
 * @author Tony Vaagenes
 */
public class DomProviderBuilder extends DomGenericTargetBuilder<Provider> {

    /**
     * Retrieves all possible provider specific parameters
     */
    private static class ProviderReader {

        final String type;
        final String clusterName;

        ProviderReader(Element providerElement) {
            type = readType(providerElement);
            clusterName = readCluster(providerElement);
        }

        private String getAttributeOrNull(Element element, String name) {
            String value = element.getAttribute(name);
            return value.isEmpty() ? null : value;
        }

        private String readCluster(Element providerElement) {
            return getAttributeOrNull(providerElement, "cluster");
        }

        private String readType(Element providerElement) {
            return getAttributeOrNull(providerElement, "type");
        }
    }

    public DomProviderBuilder(Map<String, ComponentsBuilder.ComponentType<?>> outerSearcherTypeByComponentName) {
        super(outerSearcherTypeByComponentName);
    }

    @Override
    protected Provider buildChain(DeployState deployState, TreeConfigProducer<AnyConfigProducer> ancestor, Element providerElement,
                                  ChainSpecification specWithoutInnerComponents) {

        ProviderReader providerReader = new ProviderReader(providerElement);
        FederationOptions federationOptions = readFederationOptions(providerElement);

        Provider provider = buildProvider(specWithoutInnerComponents, providerReader, federationOptions);

        Collection<Source> sources = buildSources(deployState, ancestor, providerElement);
        addSources(provider, sources);

        return provider;
    }


    private Collection<Source> buildSources(DeployState deployState, TreeConfigProducer<AnyConfigProducer> ancestor, Element providerElement) {
        List<Source> sources = new ArrayList<>();
        for (Element sourceElement : XML.getChildren(providerElement, "source")) {
            sources.add(new DomSourceBuilder(outerComponentTypeByComponentName).build(deployState, ancestor, sourceElement));
        }
        return sources;
    }

    private void addSources(Provider provider, Collection<Source> sources) {
        for (Source source : sources) {
            provider.addSource(source);
        }
    }

    private Provider buildProvider(ChainSpecification specWithoutInnerSearchers,
                                   ProviderReader providerReader,
                                   FederationOptions federationOptions) {
        if (providerReader.type == null) {
            return new GenericProvider(specWithoutInnerSearchers, federationOptions);
        } else if (LocalProviderSpec.includesType(providerReader.type)) {
            return buildLocalProvider(specWithoutInnerSearchers, providerReader, federationOptions);
        } else {
            throw new IllegalArgumentException("Unknown provider type '" + providerReader.type + "'");
        }
    }

    private Provider buildLocalProvider(ChainSpecification specWithoutInnerSearchers,
                                        ProviderReader providerReader,
                                        FederationOptions federationOptions) {
        try {
            return new LocalProvider(specWithoutInnerSearchers,
                                     federationOptions,
                                     new LocalProviderSpec(providerReader.clusterName));
        } catch (Exception e) {
            throw new RuntimeException("Failed creating local provider " + specWithoutInnerSearchers.componentId, e);
        }
    }

    public static class Node {

        public final String host;
        public final int port;

        public Node(String host, int port) {
            this.host = host;
            this.port = port;
        }

        @Override
        public String toString() {
            return "Node{" + "host='" + host + '\'' + ", port=" + port + '}';
        }
    }

}
