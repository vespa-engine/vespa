// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.builder.xml.dom.chains.search;

import com.yahoo.component.ComponentId;
import com.yahoo.component.chain.model.ChainSpecification;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.producer.AbstractConfigProducer;
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
        final String path;
        final Double cacheWeight;
        final Integer retries;
        final Double readTimeout;
        final Double connectionTimeout;
        final Double connectionPoolTimeout;
        final String clusterName;
        final List<Node> nodes;

        ProviderReader(Element providerElement) {
            type = readType(providerElement);
            path = readPath(providerElement);
            cacheWeight = readCacheWeight(providerElement);
            clusterName = readCluster(providerElement);
            readTimeout = readReadTimeout(providerElement);
            connectionTimeout = readConnectionTimeout(providerElement);
            connectionPoolTimeout = readConnectionPoolTimeout(providerElement);
            retries = readRetries(providerElement);
            nodes = readNodes(providerElement);
        }

        private String getAttributeOrNull(Element element, String name) {
            String value = element.getAttribute(name);
            return value.isEmpty() ? null : value;
        }

        private String readPath(Element providerElement) {
            return getAttributeOrNull(providerElement, "path");
        }

        private String readCluster(Element providerElement) {
            return getAttributeOrNull(providerElement, "cluster");
        }

        private Double readCacheWeight(Element providerElement) {
            String cacheWeightString = getAttributeOrNull(providerElement, "cacheweight");
            return (cacheWeightString == null)? null : Double.parseDouble(cacheWeightString);
        }

        private Integer readRetries(Element providerElement) {
            String retriesString = getAttributeOrNull(providerElement, "retries");
            return (retriesString == null) ? null : Integer.parseInt(retriesString);
        }

        private Double readReadTimeout(Element providerElement) {
            String timeoutString = getAttributeOrNull(providerElement, "readtimeout");
            return (timeoutString == null) ? null : TimeParser.seconds(timeoutString);
        }

        private Double readConnectionTimeout(Element providerElement) {
            String timeoutString = getAttributeOrNull(providerElement, "connectiontimeout");
            return (timeoutString == null) ? null : TimeParser.seconds(timeoutString);
        }

        private Double readConnectionPoolTimeout(Element providerElement) {
            String timeoutString = getAttributeOrNull(providerElement, "connectionpooltimeout");
            return (timeoutString == null) ? null : TimeParser.seconds(timeoutString);
        }

        private List<Node> readNodes(Element providerElement) {
            Element nodesSpec = XML.getChild(providerElement, "nodes");
            if (nodesSpec == null) {
                return null;
            }

            List<Node> nodes = new ArrayList<>();
            for (Element nodeSpec : XML.getChildren(nodesSpec, "node")) {
                nodes.add(readNode(nodeSpec));
            }
            return nodes;
        }

        private Node readNode(Element nodeElement) {
            String host = getAttributeOrNull(nodeElement, "host");
            // The direct calls to parse methods below works because the schema
            // guarantees us no null references
            int port = Integer.parseInt(getAttributeOrNull(nodeElement, "port"));
            return new Node(host, port);
        }

        private String readType(Element providerElement) {
            return getAttributeOrNull(providerElement, "type");
        }
    }

    public DomProviderBuilder(Map<String, ComponentsBuilder.ComponentType<?>> outerSearcherTypeByComponentName) {
        super(outerSearcherTypeByComponentName);
    }

    @Override
    protected Provider buildChain(DeployState deployState, AbstractConfigProducer<?> ancestor, Element providerElement,
                                  ChainSpecification specWithoutInnerComponents) {

        ProviderReader providerReader = new ProviderReader(providerElement);
        FederationOptions federationOptions = readFederationOptions(providerElement);

        Provider provider = buildProvider(specWithoutInnerComponents, providerReader, federationOptions);

        Collection<Source> sources = buildSources(deployState, ancestor, providerElement);
        addSources(provider, sources);

        return provider;
    }


    private Collection<Source> buildSources(DeployState deployState, AbstractConfigProducer<?> ancestor, Element providerElement) {
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
            ensureEmpty(specWithoutInnerSearchers.componentId,
                        providerReader.cacheWeight,
                        providerReader.path,
                        providerReader.nodes,
                        providerReader.readTimeout,
                        providerReader.connectionTimeout,
                        providerReader.connectionPoolTimeout,
                        providerReader.retries);

            return new LocalProvider(specWithoutInnerSearchers,
                                     federationOptions,
                                     new LocalProviderSpec(providerReader.clusterName));
        } catch (Exception e) {
            throw new RuntimeException("Failed creating local provider " + specWithoutInnerSearchers.componentId, e);
        }
    }

    private void ensureEmpty(ComponentId componentId, Object... objects) {
        for (Object object : objects) {
            if (object != null) {
                throw new IllegalArgumentException("Invalid provider option in provider '" + componentId + "': value='" + object + "'");
            }
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
