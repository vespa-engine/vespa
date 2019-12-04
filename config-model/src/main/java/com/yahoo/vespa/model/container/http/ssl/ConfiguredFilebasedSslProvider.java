// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.http.ssl;

import com.yahoo.component.ComponentId;
import com.yahoo.container.bundle.BundleInstantiationSpecification;
import com.yahoo.jdisc.http.ConnectorConfig;
import com.yahoo.jdisc.http.ssl.impl.ConfiguredSslContextFactoryProvider;
import com.yahoo.osgi.provider.model.ComponentModel;
import com.yahoo.vespa.model.container.component.SimpleComponent;

import java.util.List;
import java.util.Optional;

import static com.yahoo.component.ComponentSpecification.fromString;

/**
 * Configure SSL using file references
 *
 * @author mortent
 * @author bjorncs
 */
public class ConfiguredFilebasedSslProvider extends SimpleComponent implements ConnectorConfig.Producer {
    public static final String COMPONENT_ID_PREFIX = "configured-ssl-provider@";
    public static final String COMPONENT_CLASS = ConfiguredSslContextFactoryProvider.class.getName();
    public static final String COMPONENT_BUNDLE = "jdisc_http_service";

    private final String privateKeyPath;
    private final String certificatePath;
    private final String caCertificatePath;
    private final ConnectorConfig.Ssl.ClientAuth.Enum clientAuthentication;
    private final List<String> cipherSuites;
    private final List<String> protocolVersions;

    public ConfiguredFilebasedSslProvider(String servername,
                                          String privateKeyPath,
                                          String certificatePath,
                                          String caCertificatePath,
                                          String clientAuthentication,
                                          List<String> cipherSuites,
                                          List<String> protocolVersions) {
        super(new ComponentModel(
                new BundleInstantiationSpecification(new ComponentId(COMPONENT_ID_PREFIX+servername),
                                                     fromString(COMPONENT_CLASS),
                                                     fromString(COMPONENT_BUNDLE))));
        this.privateKeyPath = privateKeyPath;
        this.certificatePath = certificatePath;
        this.caCertificatePath = caCertificatePath;
        this.clientAuthentication = mapToConfigEnum(clientAuthentication);
        this.cipherSuites = cipherSuites;
        this.protocolVersions = protocolVersions;
    }

    @Override
    public void getConfig(ConnectorConfig.Builder builder) {
        builder.ssl(
                new ConnectorConfig.Ssl.Builder()
                        .enabled(true)
                        .privateKeyFile(privateKeyPath)
                        .certificateFile(certificatePath)
                        .caCertificateFile(Optional.ofNullable(caCertificatePath).orElse(""))
                        .clientAuth(clientAuthentication)
                        .enabledCipherSuites(cipherSuites)
                        .enabledProtocols(protocolVersions));
    }

    public SimpleComponent getComponent() {
        return new SimpleComponent(new ComponentModel(getComponentId().stringValue(), COMPONENT_CLASS, COMPONENT_BUNDLE));
    }

    private static ConnectorConfig.Ssl.ClientAuth.Enum mapToConfigEnum(String clientAuthValue) {
        if ("disabled".equals(clientAuthValue)) {
            return ConnectorConfig.Ssl.ClientAuth.Enum.DISABLED;
        } else if ("want".equals(clientAuthValue)) {
            return ConnectorConfig.Ssl.ClientAuth.Enum.WANT_AUTH;
        } else if ("need".equals(clientAuthValue)) {
            return ConnectorConfig.Ssl.ClientAuth.Enum.NEED_AUTH;
        } else {
            return ConnectorConfig.Ssl.ClientAuth.Enum.DISABLED;
        }
    }
}
