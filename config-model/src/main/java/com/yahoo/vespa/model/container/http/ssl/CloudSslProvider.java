// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.http.ssl;

import com.yahoo.jdisc.http.ConnectorConfig;
import com.yahoo.jdisc.http.ssl.impl.CloudSslContextProvider;
import com.yahoo.jdisc.http.ssl.impl.ConfiguredSslContextFactoryProvider;

import java.util.Optional;

import static com.yahoo.jdisc.http.ConnectorConfig.Ssl.ClientAuth;

/**
 * Configure SSL with PEM encoded certificate/key strings
 *
 * @author mortent
 * @author andreer
 */
public class CloudSslProvider extends SslProvider {
    public static final String COMPONENT_ID_PREFIX = "configured-ssl-provider@";
    public static final String MTLSONLY_COMPONENT_CLASS = ConfiguredSslContextFactoryProvider.class.getName();
    public static final String TOKEN_COMPONENT_CLASS = CloudSslContextProvider.class.getName();

    private final String privateKey;
    private final String certificate;
    private final String caCertificatePath;
    private final String caCertificate;
    private final ClientAuth.Enum clientAuthentication;

    public CloudSslProvider(String servername, String privateKey, String certificate, String caCertificatePath, String caCertificate, ClientAuth.Enum clientAuthentication, boolean enableTokenSupport) {
        super(COMPONENT_ID_PREFIX, servername, componentClass(enableTokenSupport), null);
        this.privateKey = privateKey;
        this.certificate = certificate;
        this.caCertificatePath = caCertificatePath;
        this.caCertificate = caCertificate;
        this.clientAuthentication = clientAuthentication;
    }

    private static String componentClass(boolean enableTokenSupport) {
        return enableTokenSupport ? TOKEN_COMPONENT_CLASS : MTLSONLY_COMPONENT_CLASS;
    }

    @Override
    public void amendConnectorConfig(ConnectorConfig.Builder builder) {
        builder.ssl.enabled(true);
        builder.ssl.privateKey(privateKey);
        builder.ssl.certificate(certificate);
        builder.ssl.caCertificateFile(Optional.ofNullable(caCertificatePath).orElse(""));
        builder.ssl.caCertificate(Optional.ofNullable(caCertificate).orElse(""));
        builder.ssl.clientAuth(clientAuthentication);
    }
}
