// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.search.searchchain;

import com.yahoo.binaryprefix.BinaryPrefix;
import com.yahoo.binaryprefix.BinaryScaledAmount;
import com.yahoo.component.chain.model.ChainSpecification;
import com.yahoo.search.cache.QrBinaryCacheConfig;
import com.yahoo.search.cache.QrBinaryCacheRegionConfig;
import com.yahoo.search.federation.ProviderConfig;
import com.yahoo.search.searchchain.model.federation.FederationOptions;
import com.yahoo.search.searchchain.model.federation.HttpProviderSpec;

import java.util.ArrayList;
import java.util.List;

import static com.yahoo.search.federation.ProviderConfig.Node;
import static com.yahoo.search.federation.ProviderConfig.Yca;


/**
 * A provider containing a http searcher.
 * @author Tony Vaagenes
 */
public class HttpProvider extends Provider implements ProviderConfig.Producer,
        QrBinaryCacheConfig.Producer,
        QrBinaryCacheRegionConfig.Producer {

    private final HttpProviderSpec providerSpec;

    //TODO: For backward compatibility only, eliminate this later
    private BinaryScaledAmount cacheSize;

    public double getCacheWeight() {
        return providerSpec.cacheWeight;
    }

    /**
     * TODO: remove, for backward compatibility only.
     */
    public void setCacheSize(BinaryScaledAmount cacheSize) {
        this.cacheSize = cacheSize;
    }

    /*
     * Config producer for the contained http searcher..
     */

    public HttpProvider(ChainSpecification specWithoutInnerSearchers, FederationOptions federationOptions, HttpProviderSpec providerSpec) {
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

    private static Yca.Builder getCertificate(HttpProviderSpec providerSpec) {
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

    private static List<Node.Builder> getNodes(List<HttpProviderSpec.Node> nodeSpecs) {
        ArrayList<Node.Builder> nodes = new ArrayList<>();
        for (HttpProviderSpec.Node node : nodeSpecs) {
            nodes.add(
                    new Node.Builder()
                            .host(node.host)
                            .port(node.port));
        }
        return nodes;
    }

    public int cacheSizeMB() {
        return providerSpec.cacheSizeMB != null ?
                providerSpec.cacheSizeMB :
                (int) cacheSize.as(BinaryPrefix.mega);
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
