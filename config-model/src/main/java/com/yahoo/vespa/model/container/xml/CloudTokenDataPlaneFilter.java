// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.xml;

import com.yahoo.component.ComponentSpecification;
import com.yahoo.component.chain.dependencies.Dependencies;
import com.yahoo.component.chain.model.ChainedComponentModel;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.provision.DataplaneToken;
import com.yahoo.container.bundle.BundleInstantiationSpecification;
import com.yahoo.jdisc.http.filter.security.cloud.config.CloudTokenDataPlaneFilterConfig;
import com.yahoo.vespa.model.container.ApplicationContainerCluster;
import com.yahoo.vespa.model.container.http.Client;
import com.yahoo.vespa.model.container.http.Filter;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

class CloudTokenDataPlaneFilter extends Filter implements CloudTokenDataPlaneFilterConfig.Producer {
    private final Collection<Client> clients;
    private final String tokenContext;

    CloudTokenDataPlaneFilter(ApplicationContainerCluster cluster, DeployState state) {
        super(model());
        this.clients = List.copyOf(cluster.getClients());
        // Token domain must be identical to the domain used for generating the tokens
        this.tokenContext = "Vespa Cloud tenant data plane:%s"
                .formatted(state.getProperties().applicationId().tenant().value());
    }

    private static ChainedComponentModel model() {
        return new ChainedComponentModel(
                new BundleInstantiationSpecification(
                        new ComponentSpecification("com.yahoo.jdisc.http.filter.security.cloud.CloudTokenDataPlaneFilter"),
                        null,
                        new ComponentSpecification("jdisc-security-filters")),
                Dependencies.emptyDependencies());
    }

    @Override
    public void getConfig(CloudTokenDataPlaneFilterConfig.Builder builder) {
        var clientsCfg = clients.stream()
                .filter(c -> !c.tokens().isEmpty())
                .map(x -> new CloudTokenDataPlaneFilterConfig.Clients.Builder()
                        .id(x.id())
                        .tokens(tokensConfig(x.tokens()))
                        .permissions(x.permissions()))
                .toList();
        builder.clients(clientsCfg).tokenContext(tokenContext);
    }

    private static List<CloudTokenDataPlaneFilterConfig.Clients.Tokens.Builder> tokensConfig(Collection<DataplaneToken> tokens) {
        return tokens.stream()
                .map(token -> new CloudTokenDataPlaneFilterConfig.Clients.Tokens.Builder()
                        .id(token.tokenId())
                        .fingerprints(token.versions().stream().map(DataplaneToken.Version::fingerprint).toList())
                        .checkAccessHashes(token.versions().stream().map(DataplaneToken.Version::checkAccessHash).toList())
                        .expirations(token.versions().stream().map(v -> v.expiration().map(Instant::toString).orElse("<none>")).toList()))
                .toList();
    }

}
