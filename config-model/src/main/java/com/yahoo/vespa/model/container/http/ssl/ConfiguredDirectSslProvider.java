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
import static com.yahoo.jdisc.http.ConnectorConfig.Ssl.*;

/**
 * Configure SSL with PEM encoded certificate/key strings
 *
 * @author mortent
 * @author andreer
 */
public class ConfiguredDirectSslProvider extends SimpleComponent implements ConnectorConfig.Producer {
    public static final String COMPONENT_ID_PREFIX = "configured-ssl-provider@";
    public static final String COMPONENT_CLASS = ConfiguredSslContextFactoryProvider.class.getName();
    public static final String COMPONENT_BUNDLE = "jdisc_http_service";

    private final String privateKey;
    private final String certificate;
    private final String caCertificatePath;
    private final String caCertificate;
    private final ClientAuth.Enum clientAuthentication;

    public ConfiguredDirectSslProvider(String servername, String privateKey, String certificate, String caCertificatePath, String caCertificate, ClientAuth.Enum clientAuthentication) {
        super(new ComponentModel(
                new BundleInstantiationSpecification(new ComponentId(COMPONENT_ID_PREFIX+servername),
                                                     fromString(COMPONENT_CLASS),
                                                     fromString(COMPONENT_BUNDLE))));
        this.privateKey = privateKey;
        this.certificate = certificate;
        this.caCertificatePath = caCertificatePath;
        this.caCertificate = caCertificate;
        this.clientAuthentication = clientAuthentication;
    }

    @Override
    public void getConfig(ConnectorConfig.Builder builder) {
        builder.ssl.enabled(true);
        builder.ssl.privateKey(privateKey);
        builder.ssl.certificate(certificate);
        builder.ssl.caCertificateFile(Optional.ofNullable(caCertificatePath).orElse(""));
        builder.ssl.caCertificate(Optional.ofNullable(caCertificate).orElse(""));
        builder.ssl.clientAuth(clientAuthentication);
    }

    public SimpleComponent getComponent() {
        return new SimpleComponent(new ComponentModel(getComponentId().stringValue(), COMPONENT_CLASS, COMPONENT_BUNDLE));
    }

}
