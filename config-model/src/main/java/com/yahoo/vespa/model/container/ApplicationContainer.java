// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container;

import com.yahoo.config.model.api.TlsSecrets;
import com.yahoo.config.model.api.container.ContainerServiceType;
import com.yahoo.config.model.producer.AbstractConfigProducer;
import com.yahoo.vespa.model.container.http.ConnectorFactory;
import com.yahoo.vespa.model.container.http.JettyHttpServer;
import com.yahoo.vespa.model.container.http.ssl.ConfiguredDirectSslProvider;

import java.util.Optional;

/**
 * A container that is typically used by container clusters set up from the user application.
 *
 * @author gjoranv
 */
public final class ApplicationContainer extends Container {

    private static final String defaultHostedJVMArgs = "-XX:+UseOSErrorReporting -XX:+SuppressFatalErrorMessage";

    private final boolean isHostedVespa;

    public ApplicationContainer(AbstractConfigProducer parent, String name, int index, boolean isHostedVespa, Optional<TlsSecrets> tlsSecrets) {
        this(parent, name, false, index, isHostedVespa, tlsSecrets);
    }

    public ApplicationContainer(AbstractConfigProducer parent, String name, boolean retired, int index, boolean isHostedVespa, Optional<TlsSecrets> tlsSecrets) {
        super(parent, name, retired, index);
        this.isHostedVespa = isHostedVespa;

        if (isHostedVespa && tlsSecrets.isPresent()) {
            String connectorName = "tls4443";
            JettyHttpServer server = getDefaultHttpServer();
            if(getHttp() != null) {
                server = getHttp().getHttpServer();
            }
            server.addConnector(new ConnectorFactory(connectorName, 4443,
                                                     new ConfiguredDirectSslProvider(server.getComponentId().getName(), tlsSecrets.get().key(), tlsSecrets.get().certificate(), null, null)));
        }
    }

    @Override
    protected ContainerServiceType myServiceType() {
        if (parent instanceof ContainerCluster) {
            ContainerCluster cluster = (ContainerCluster)parent;
            // TODO: The 'qrserver' name is retained for legacy reasons (e.g. system tests and log parsing).
            if (cluster.getSearch() != null && cluster.getDocproc() == null && cluster.getDocumentApi() == null) {
                return ContainerServiceType.QRSERVER;
            }
        }
        return ContainerServiceType.CONTAINER;
    }

    /** Returns the jvm arguments this should start with */
    @Override
    public String getJvmOptions() {
        String jvmArgs = super.getJvmOptions();
        return isHostedVespa && hasDocproc()
                ? ("".equals(jvmArgs) ? defaultHostedJVMArgs : defaultHostedJVMArgs + " " + jvmArgs)
                : jvmArgs;
    }

    private boolean hasDocproc() {
        return (parent instanceof ContainerCluster) && (((ContainerCluster)parent).getDocproc() != null);
    }

}
