// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation;

import com.yahoo.vespa.model.application.validation.Validation.Context;
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
public class CloudHttpConnectorValidator implements Validator {
    @Override
    public void validate(Context context) {
        if (!context.deployState().isHostedTenantApplication(context.model().getAdmin().getApplicationType())) return;

        context.model().getContainerClusters().forEach((__, cluster) -> {
            var http = cluster.getHttp();
            if (http == null) return;
            var illegalConnectors = http.getHttpServer().stream().flatMap(s -> s.getConnectorFactories().stream()
                    .filter(c -> !isAllowedConnector(c)))
                    .map(cf -> "%s@%d".formatted(cf.getName(), cf.getListenPort()))
                    .toList();
            if (illegalConnectors.isEmpty()) return;
            context.illegal(
                    ("Adding additional or modifying existing HTTPS connectors is not allowed for Vespa Cloud applications." +
                            " Violating connectors: %s. See https://cloud.vespa.ai/en/security/whitepaper, " +
                            "https://cloud.vespa.ai/en/security/guide#data-plane.")
                            .formatted(illegalConnectors));
        });
    }

    private static boolean isAllowedConnector(ConnectorFactory cf) {
        return cf instanceof HostedSslConnectorFactory
                || cf.getClass().getSimpleName().endsWith("HealthCheckProxyConnector") // TODO(bjorncs, 2024-11-13): remove once migrated
                || cf.getClass().getPackageName().startsWith("com.yahoo.vespa.model.container.amender")
                || (cf.getListenPort() == Container.BASEPORT && cf.sslProvider() instanceof DefaultSslProvider);
    }
}
