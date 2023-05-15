// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.model.application.validation;

import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.container.Container;
import com.yahoo.vespa.model.container.http.ConnectorFactory;
import com.yahoo.vespa.model.container.http.ssl.DefaultSslProvider;
import com.yahoo.vespa.model.container.http.ssl.HostedSslConnectorFactory;

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
            var illegalConnectors = http.getHttpServer().stream().flatMap(s -> s.getConnectorFactories().stream()
                    .filter(c -> !isAllowedConnector(c)))
                    .map(cf -> "%s@%d".formatted(cf.getName(), cf.getListenPort()))
                    .toList();
            if (illegalConnectors.isEmpty()) return;
            throw new IllegalArgumentException(
                    ("Adding additional or modifying existing HTTPS connectors is not allowed for Vespa Cloud applications." +
                            " Violating connectors: %s. See https://cloud.vespa.ai/en/security/whitepaper, " +
                            "https://cloud.vespa.ai/en/security/guide#data-plane.")
                            .formatted(illegalConnectors));
        });
    }

    private static boolean isAllowedConnector(ConnectorFactory cf) {
        return cf instanceof HostedSslConnectorFactory
                || cf.getClass().getSimpleName().endsWith("HealthCheckProxyConnector")
                || (cf.getListenPort() == Container.BASEPORT && cf.sslProvider() instanceof DefaultSslProvider);
    }
}
