// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.http.ssl;

import com.yahoo.jdisc.http.ConnectorConfig;

import java.util.Optional;

import static com.yahoo.jdisc.http.ConnectorConfig.Ssl.ClientAuth;

/**
 * Configure SSL with PEM encoded certificate/key strings
 *
 * @author mortent
 * @author andreer
 */
public class CloudSslProvider extends SslProvider {
    private final String privateKey;
    private final String certificate;
    private final String caCertificatePath;
    private final String caCertificate;
    private final ClientAuth.Enum clientAuthentication;

    public CloudSslProvider(String servername, String privateKey, String certificate, String caCertificatePath,
                            String caCertificate, ClientAuth.Enum clientAuthentication, boolean enableTokenSupport) {
        super("cloud-ssl-provider@", servername, componentClass(enableTokenSupport), bundleName(enableTokenSupport));
        this.privateKey = privateKey;
        this.certificate = certificate;
        this.caCertificatePath = caCertificatePath;
        this.caCertificate = caCertificate;
        this.clientAuthentication = clientAuthentication;
    }

    private static String componentClass(boolean enableTokenSupport) {
        return enableTokenSupport
                ? "com.yahoo.vespa.cloud.tenant.dataplane.CloudTokenSslContextProvider"
                : "com.yahoo.jdisc.http.ssl.impl.ConfiguredSslContextFactoryProvider";
    }

    private static String bundleName(boolean enableTokenSupport) {
        return enableTokenSupport ? "cloud-tenant" : null;
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
