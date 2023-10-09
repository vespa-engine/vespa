// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.xml;

import com.yahoo.component.ComponentSpecification;
import com.yahoo.component.chain.dependencies.Dependencies;
import com.yahoo.component.chain.model.ChainedComponentModel;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.container.bundle.BundleInstantiationSpecification;
import com.yahoo.jdisc.http.filter.security.cloud.config.CloudDataPlaneFilterConfig;
import com.yahoo.security.X509CertificateUtils;
import com.yahoo.vespa.model.container.ApplicationContainerCluster;
import com.yahoo.vespa.model.container.http.Client;
import com.yahoo.vespa.model.container.http.Filter;

import java.util.Collection;
import java.util.List;

class CloudDataPlaneFilter extends Filter implements CloudDataPlaneFilterConfig.Producer {

    private static final String CLASS = "com.yahoo.jdisc.http.filter.security.cloud.CloudDataPlaneFilter";
    private static final String BUNDLE = "jdisc-security-filters";

    private final Collection<Client> clients;
    private final boolean clientsLegacyMode;

    CloudDataPlaneFilter(ApplicationContainerCluster cluster, DeployState state) {
        super(model());
        this.clients = List.copyOf(cluster.getClients());
        this.clientsLegacyMode = cluster.clientsLegacyMode();
    }

    private static ChainedComponentModel model() {
        return new ChainedComponentModel(
                new BundleInstantiationSpecification(
                        new ComponentSpecification(CLASS), null, new ComponentSpecification(BUNDLE)),
                Dependencies.emptyDependencies());
    }

    @Override
    public void getConfig(CloudDataPlaneFilterConfig.Builder builder) {
        if (clientsLegacyMode) {
            builder.legacyMode(true);
        } else {
            var clientsCfg = clients.stream()
                    .filter(c -> !c.certificates().isEmpty())
                    .map(x -> new CloudDataPlaneFilterConfig.Clients.Builder()
                            .id(x.id())
                            .certificates(x.certificates().stream().map(X509CertificateUtils::toPem).toList())
                            .permissions(x.permissions()))
                    .toList();
            builder.clients(clientsCfg).legacyMode(false);
        }
    }


}
