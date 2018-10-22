// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.search.searchchain;

import com.yahoo.binaryprefix.BinaryPrefix;
import com.yahoo.binaryprefix.BinaryScaledAmount;
import com.yahoo.component.chain.model.ChainSpecification;
import com.yahoo.search.cache.QrBinaryCacheConfig;
import com.yahoo.search.cache.QrBinaryCacheRegionConfig;
import com.yahoo.search.federation.ProviderConfig;
import com.yahoo.search.searchchain.model.federation.FederationOptions;

import java.util.ArrayList;
import java.util.List;

import static com.yahoo.search.federation.ProviderConfig.Node;
import static com.yahoo.search.federation.ProviderConfig.Yca;


/**
 * A provider containing a http searcher.
 *
 * @author Tony Vaagenes
 */
public class HttpProvider extends Provider implements ProviderConfig.Producer,
                                                      QrBinaryCacheConfig.Producer,
                                                      QrBinaryCacheRegionConfig.Producer {

    @SuppressWarnings("deprecation")
    private final com.yahoo.search.searchchain.model.federation.HttpProviderSpec providerSpec;

    /*
     * Config producer for the contained http searcher..
     */
    @SuppressWarnings("deprecation")
    public HttpProvider(ChainSpecification specWithoutInnerSearchers, FederationOptions federationOptions, com.yahoo.search.searchchain.model.federation.HttpProviderSpec providerSpec) {
        super(specWithoutInnerSearchers, federationOptions);
        this.providerSpec = providerSpec;
    }

    @Override
    public void getConfig(ProviderConfig.Builder builder) {
        if (providerSpec.path != null)
            builder.path(providerSpec.path);
        if (providerSpec.connectionParameters.readTimeout != null)
            builder.readTimeout(providerSpec.connectionParameters.readTimeout );
        if (providerSpec.connectionParameters.connectionTimeout != null)
            builder.connectionTimeout(providerSpec.connectionParameters.connectionTimeout);
        if (providerSpec.connectionParameters.connectionPoolTimeout != null)
            builder.connectionPoolTimeout(providerSpec.connectionParameters.connectionPoolTimeout);
        if (providerSpec.connectionParameters.retries != null)
            builder.retries(providerSpec.connectionParameters.retries);

        builder.node(getNodes(providerSpec.nodes));

        if (providerSpec.ycaApplicationId != null) {
            builder.yca(getCertificate(providerSpec));
        }
    }

    @SuppressWarnings("deprecation")
    private static Yca.Builder getCertificate(com.yahoo.search.searchchain.model.federation.HttpProviderSpec providerSpec) {
        Yca.Builder certificate = new Yca.Builder()
                .applicationId(providerSpec.ycaApplicationId);

        if (providerSpec.ycaProxy != null) {
            certificate.useProxy(true);
            if (providerSpec.ycaProxy.host != null) {
                certificate.host(providerSpec.ycaProxy.host)
                        .port(providerSpec.ycaProxy.port);
            }
        }
        if (providerSpec.ycaCertificateTtl != null) certificate.ttl(providerSpec.ycaCertificateTtl);
        if (providerSpec.ycaRetryWait != null) certificate.ttl(providerSpec.ycaRetryWait);
        return certificate;
    }

    @SuppressWarnings("deprecation")
    private static List<Node.Builder> getNodes(List<com.yahoo.search.searchchain.model.federation.HttpProviderSpec.Node> nodeSpecs) {
        ArrayList<Node.Builder> nodes = new ArrayList<>();
        for (com.yahoo.search.searchchain.model.federation.HttpProviderSpec.Node node : nodeSpecs) {
            nodes.add(
                    new Node.Builder()
                            .host(node.host)
                            .port(node.port));
        }
        return nodes;
    }

    public int cacheSizeMB() {
        return providerSpec.cacheSizeMB != null ? providerSpec.cacheSizeMB : 0;
    }

    @Override
    public void getConfig(QrBinaryCacheConfig.Builder builder) {
        builder.cache_size(cacheSizeMB());
    }

    @Override
    public void getConfig(QrBinaryCacheRegionConfig.Builder builder) {
        builder.region_size(cacheSizeMB());
    }
}
