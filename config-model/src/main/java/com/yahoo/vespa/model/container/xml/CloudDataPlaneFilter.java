// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.xml;

import com.yahoo.component.ComponentSpecification;
import com.yahoo.component.chain.dependencies.Dependencies;
import com.yahoo.component.chain.model.ChainedComponentModel;
import com.yahoo.container.bundle.BundleInstantiationSpecification;
import com.yahoo.jdisc.http.filter.security.cloud.config.CloudDataPlaneFilterConfig;
import com.yahoo.security.X509CertificateUtils;
import com.yahoo.vespa.model.container.ApplicationContainerCluster;
import com.yahoo.vespa.model.container.http.Client;
import com.yahoo.vespa.model.container.http.Filter;

import java.util.List;

class CloudDataPlaneFilter extends Filter implements CloudDataPlaneFilterConfig.Producer {

    private static final String CLASS = "com.yahoo.jdisc.http.filter.security.cloud.CloudDataPlaneFilter";
    private static final String BUNDLE = "jdisc-security-filters";

    private final ApplicationContainerCluster cluster;
    private final boolean legacyMode;

    CloudDataPlaneFilter(ApplicationContainerCluster cluster, boolean legacyMode) {
        super(model());
        this.cluster = cluster;
        this.legacyMode = legacyMode;
    }

    private static ChainedComponentModel model() {
        return new ChainedComponentModel(
                new BundleInstantiationSpecification(
                        new ComponentSpecification(CLASS), null, new ComponentSpecification(BUNDLE)),
                Dependencies.emptyDependencies());
    }

    @Override
    public void getConfig(CloudDataPlaneFilterConfig.Builder builder) {
        if (legacyMode) {
            builder.legacyMode(true);
        } else {
            List<Client> clients = cluster.getClients();
            builder.legacyMode(false);
            List<CloudDataPlaneFilterConfig.Clients.Builder> clientsList = clients.stream()
                    .map(x -> new CloudDataPlaneFilterConfig.Clients.Builder()
                            .id(x.id())
                            .certificates(X509CertificateUtils.toPem(x.certificates()))
                            .permissions(x.permissions()))
                    .toList();
            builder.clients(clientsList);
        }
    }
}
