// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.http.ssl;

import com.yahoo.component.ComponentId;
import com.yahoo.container.bundle.BundleInstantiationSpecification;
import com.yahoo.jdisc.http.ConnectorConfig;
import com.yahoo.jdisc.http.ssl.impl.ConfiguredSslContextFactoryProvider;
import com.yahoo.osgi.provider.model.ComponentModel;
import com.yahoo.vespa.model.container.component.SimpleComponent;

import java.util.Optional;

import static com.yahoo.component.ComponentSpecification.fromString;

/**
 * Configure SSL using file references
 *
 * @author mortent
 */
public class ConfiguredFilebasedSslProvider extends SimpleComponent implements ConnectorConfig.Producer {
    public static final String COMPONENT_ID_PREFIX = "configured-ssl-provider@";
    public static final String COMPONENT_CLASS = ConfiguredSslContextFactoryProvider.class.getName();
    public static final String COMPONENT_BUNDLE = "jdisc_http_service";

    private final String privateKeyPath;
    private final String certificatePath;
    private final String caCertificatePath;
    private final ConnectorConfig.Ssl.ClientAuth.Enum clientAuthentication;

    public ConfiguredFilebasedSslProvider(String servername, String privateKeyPath, String certificatePath, String caCertificatePath, String clientAuthentication) {
        super(new ComponentModel(
                new BundleInstantiationSpecification(new ComponentId(COMPONENT_ID_PREFIX+servername),
                                                     fromString(COMPONENT_CLASS),
                                                     fromString(COMPONENT_BUNDLE))));
        this.privateKeyPath = privateKeyPath;
        this.certificatePath = certificatePath;
        this.caCertificatePath = caCertificatePath;
        this.clientAuthentication = mapToConfigEnum(clientAuthentication);
    }

    @Override
    public void getConfig(ConnectorConfig.Builder builder) {
        builder.ssl.enabled(true);
        builder.ssl.privateKeyFile(privateKeyPath);
        builder.ssl.certificateFile(certificatePath);
        builder.ssl.caCertificateFile(Optional.ofNullable(caCertificatePath).orElse(""));
        builder.ssl.clientAuth(clientAuthentication);
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
