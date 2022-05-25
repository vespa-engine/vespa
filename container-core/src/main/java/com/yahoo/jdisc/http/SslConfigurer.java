// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http;

import javax.net.ssl.SSLContext;
import java.security.KeyStore;
import java.util.List;

/**
 * Provides SSL/TLS configuration for a server connector.
 *
 * @author bjorncs
 */
public interface SslProvider extends AutoCloseable {

    interface ConnectorSsl {
        enum ClientAuth { DISABLED, WANT, NEED }
        ConnectorSsl setSslContext(SSLContext ctx);
        ConnectorSsl setClientAuth(ConnectorSsl.ClientAuth auth);
        ConnectorSsl setEnabledCipherSuites(List<String> ciphers);
        ConnectorSsl setEnabledProtocolVersions(List<String> versions);
        ConnectorSsl setKeystore(KeyStore keystore, char[] password);
        ConnectorSsl setKeystore(KeyStore keystore);
        ConnectorSsl setTruststore(KeyStore truststore, char[] password);
        ConnectorSsl setTruststore(KeyStore truststore);
    }

    /**
     * Invoked during configuration of server connector
     * @param ssl provides methods to modify default SSL configuration
     * @param name The connector name
     * @param port The connector listen port
     */
    void configureSsl(ConnectorSsl ssl, String name, int port);

    @Override default void close() {}
}
