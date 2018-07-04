// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.builder.xml.dom.chains.search;

import com.yahoo.binaryprefix.BinaryPrefix;
import com.yahoo.component.chain.dependencies.Dependencies;
import com.yahoo.component.chain.model.ChainedComponentModel;
import com.yahoo.component.ComponentId;
import com.yahoo.component.chain.model.ChainSpecification;
import com.yahoo.search.searchchain.model.federation.FederationOptions;
import com.yahoo.search.searchchain.model.federation.HttpProviderSpec;
import com.yahoo.search.searchchain.model.federation.LocalProviderSpec;
import com.yahoo.text.XML;
import com.yahoo.config.model.producer.AbstractConfigProducer;
import com.yahoo.vespa.model.builder.xml.dom.BinaryScaledAmountParser;
import com.yahoo.vespa.model.builder.xml.dom.chains.ComponentsBuilder;
import com.yahoo.vespa.model.container.search.searchchain.HttpProvider;
import com.yahoo.vespa.model.container.search.searchchain.HttpProviderSearcher;
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
        final List<HttpProviderSpec.Node> nodes;
        final String certificateApplicationId;
        final Integer certificateTtl;
        final Integer certificateRetryWait;
        final HttpProviderSpec.Node certificateProxy;  // Just re-using the Node class, as it matches our needs
        final Integer cacheSizeMB;

        ProviderReader(Element providerElement) {
            type = readType(providerElement);
            path = readPath(providerElement);
            cacheWeight = readCacheWeight(providerElement);
            cacheSizeMB = readCacheSize(providerElement);
            clusterName = readCluster(providerElement);
            readTimeout = readReadTimeout(providerElement);
            connectionTimeout = readConnectionTimeout(providerElement);
            connectionPoolTimeout = readConnectionPoolTimeout(providerElement);
            retries = readRetries(providerElement);
            nodes = readNodes(providerElement);
            certificateApplicationId = readCertificateApplicationId(providerElement);
            certificateTtl = readCertificateTtl(providerElement);
            certificateRetryWait = readCertificateRetryWait(providerElement);
            certificateProxy = readCertificateProxy(providerElement);
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

        private Integer readCacheSize(Element providerElement) {
            String cacheSize = getAttributeOrNull(providerElement, "cachesize");
            return (cacheSize == null)? null : (int)BinaryScaledAmountParser.parse(cacheSize).as(BinaryPrefix.mega);
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

        private String readCertificateApplicationId(Element providerElement) {
            return getAttributeOrNull(providerElement, "yca-application-id");
        }

        private Integer readCertificateTtl(Element providerElement) {
            String x = getAttributeOrNull(providerElement, "yca-cache-ttl");
            return (x == null) ? null : TimeParser.seconds(x).intValue();
        }

        private Integer readCertificateRetryWait(Element providerElement) {
            String x = getAttributeOrNull(providerElement, "yca-cache-retry-wait");
            return (x == null) ? null : TimeParser.seconds(x).intValue();
        }

        private HttpProviderSpec.Node readCertificateProxy(Element providerElement) {
            Element certificateProxySpec = XML.getChild(providerElement, "yca-proxy");
            if (certificateProxySpec == null) {
                return null; // no proxy
            }
            if(getAttributeOrNull(certificateProxySpec, "host") == null) {
                return new HttpProviderSpec.Node(null, 0); // default proxy
            }
            return readNode(certificateProxySpec);
        }

        private List<HttpProviderSpec.Node> readNodes(Element providerElement) {
            Element nodesSpec = XML.getChild(providerElement, "nodes");
            if (nodesSpec == null) {
                return null;
            }

            List<HttpProviderSpec.Node> nodes = new ArrayList<>();
            for (Element nodeSpec : XML.getChildren(nodesSpec, "node")) {
                nodes.add(readNode(nodeSpec));
            }
            return nodes;
        }

        private HttpProviderSpec.Node readNode(Element nodeElement) {
            String host = getAttributeOrNull(nodeElement, "host");
            // The direct calls to parse methods below works because the schema
            // guarantees us no null references
            int port = Integer.parseInt(getAttributeOrNull(nodeElement, "port"));
            return new HttpProviderSpec.Node(host, port);
        }

        private String readType(Element providerElement) {
            return getAttributeOrNull(providerElement, "type");
        }
    }

    public DomProviderBuilder(Map<String, ComponentsBuilder.ComponentType> outerSearcherTypeByComponentName) {
        super(outerSearcherTypeByComponentName);
    }

    @Override
    protected Provider buildChain(AbstractConfigProducer ancestor, Element providerElement,
                                        ChainSpecification specWithoutInnerComponents) {

        ProviderReader providerReader = new ProviderReader(providerElement);
        if (providerReader.certificateApplicationId == null && providerReader.certificateProxy != null) {
            throw new IllegalArgumentException(
                    "Provider '" + specWithoutInnerComponents.componentId +
                            "' must have a certificate application ID, since a certificate store proxy is given");
        }

        FederationOptions federationOptions = readFederationOptions(providerElement);

        Provider provider = buildProvider(specWithoutInnerComponents, providerReader, federationOptions);

        Collection<Source> sources = buildSources(ancestor, providerElement);
        addSources(provider, sources);

        return provider;
    }


    private Collection<Source> buildSources(AbstractConfigProducer ancestor, Element providerElement) {
        List<Source> sources = new ArrayList<>();
        for (Element sourceElement : XML.getChildren(providerElement, "source")) {
            sources.add(new DomSourceBuilder(outerComponentTypeByComponentName).build(ancestor, sourceElement));
        }
        return sources;
    }

    private void addSources(Provider provider, Collection<Source> sources) {
        for (Source source : sources) {
            provider.addSource(source);
        }
    }

    private Provider buildProvider(ChainSpecification specWithoutInnerSearchers,
                                   ProviderReader providerReader, FederationOptions federationOptions) {

        if (providerReader.type == null) {
            return buildEmptyHttpProvider(specWithoutInnerSearchers, providerReader, federationOptions);
        } else if (HttpProviderSpec.includesType(providerReader.type)) {
            return buildHttpProvider(specWithoutInnerSearchers, providerReader, federationOptions);
        } else if (LocalProviderSpec.includesType(providerReader.type)) {
            return buildLocalProvider(specWithoutInnerSearchers, providerReader, federationOptions);
        } else {
            throw new RuntimeException("Unknown provider type '" + providerReader.type + "'");
        }
    }

    private Provider buildLocalProvider(ChainSpecification specWithoutInnerSearchers, ProviderReader providerReader, FederationOptions federationOptions) {
        try {
            ensureEmpty(specWithoutInnerSearchers.componentId, providerReader.cacheWeight, providerReader.path, providerReader.nodes,
                        providerReader.readTimeout, providerReader.connectionTimeout, providerReader.connectionPoolTimeout,
                        providerReader.retries, providerReader.certificateApplicationId, providerReader.certificateTtl,
                        providerReader.certificateRetryWait, providerReader.certificateProxy);

            return new LocalProvider(specWithoutInnerSearchers,
                    federationOptions,
                    new LocalProviderSpec(providerReader.clusterName, providerReader.cacheSizeMB));
        } catch (Exception e) {
            throw new RuntimeException("Failed creating local provider " + specWithoutInnerSearchers.componentId, e);
        }
    }

    private Provider buildHttpProvider(ChainSpecification specWithoutInnerSearchers, ProviderReader providerReader, FederationOptions federationOptions) {
        ensureEmpty(specWithoutInnerSearchers.componentId, providerReader.clusterName);

        Provider httpProvider = buildEmptyHttpProvider(specWithoutInnerSearchers, providerReader, federationOptions);

        httpProvider.addInnerComponent(new HttpProviderSearcher(
                new ChainedComponentModel(
                        HttpProviderSpec.toBundleInstantiationSpecification(HttpProviderSpec.Type.valueOf(providerReader.type)),
                        Dependencies.emptyDependencies())));

        return httpProvider;
    }


    private Provider buildEmptyHttpProvider(ChainSpecification specWithoutInnerSearchers, ProviderReader providerReader, FederationOptions federationOptions) {
        ensureEmpty(specWithoutInnerSearchers.componentId, providerReader.clusterName);

        return new HttpProvider(specWithoutInnerSearchers,
                federationOptions,
                new HttpProviderSpec(
                        providerReader.cacheWeight,
                        providerReader.path,
                        providerReader.nodes,
                        providerReader.certificateApplicationId,
                        providerReader.certificateTtl,
                        providerReader.certificateRetryWait,
                        providerReader.certificateProxy,
                        providerReader.cacheSizeMB,
                        connectionParameters(providerReader)));
    }

    private HttpProviderSpec.ConnectionParameters connectionParameters(ProviderReader providerReader) {
        return new HttpProviderSpec.ConnectionParameters(
                providerReader.readTimeout,
                providerReader.connectionTimeout,
                providerReader.connectionPoolTimeout,
                providerReader.retries);
    }

    private void ensureEmpty(ComponentId componentId, Object... objects) {
        for (Object object : objects) {
            if (object != null) {
                throw new RuntimeException("Invalid provider option in provider '" + componentId + "': value='" + object + "'");
            }
        }
    }
}
