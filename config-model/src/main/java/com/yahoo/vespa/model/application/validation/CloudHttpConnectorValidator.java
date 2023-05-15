// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.model.application.validation;

import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.container.Container;
import com.yahoo.vespa.model.container.http.JettyHttpServer;
import com.yahoo.vespa.model.container.http.ssl.ConfiguredDirectSslProvider;
import com.yahoo.vespa.model.container.http.ssl.DefaultSslProvider;
import com.yahoo.vespa.model.container.xml.ContainerModelBuilder;

import java.util.List;

/**
 * Enforces that Cloud applications cannot
 * 1) override connector specific TLS configuration
 * 2) add additional HTTP connectors
 *
 * @author bjorncs
 */
public class CloudHttpConnectorValidator extends Validator {
    @Override
    public void validate(VespaModel model, DeployState state) {
        if (!state.isHostedTenantApplication(model.getAdmin().getApplicationType())) return;

        model.getContainerClusters().forEach((__, cluster) -> {
            var http = cluster.getHttp();
            if (http == null) return;
            var connectors = http.getHttpServer().map(JettyHttpServer::getConnectorFactories).orElse(List.of());
            for (var connector : connectors) {
                int port = connector.getListenPort();
                if (!List.of(ContainerModelBuilder.HOSTED_VESPA_DATAPLANE_PORT, Container.BASEPORT).contains(port)) {
                    throw new IllegalArgumentException(
                            "Adding additional HTTP connectors is not allowed for Vespa Cloud applications. " +
                                    "See https://cloud.vespa.ai/en/security/whitepaper.");
                }
                var sslProvider = connector.sslProvider();
                if (!(sslProvider instanceof ConfiguredDirectSslProvider || sslProvider instanceof DefaultSslProvider)) {
                    throw new IllegalArgumentException(
                            "Overriding connector specific TLS configuration is not allowed in Vespa Cloud. " +
                                    "See https://cloud.vespa.ai/en/security/guide#data-plane.");
                }
            }
        });
    }
}
