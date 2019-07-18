// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.util.http;

import com.yahoo.security.tls.MixedMode;
import com.yahoo.security.tls.TlsContext;
import com.yahoo.security.tls.TransportSecurityUtils;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.HttpClientConnectionManager;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.BasicHttpClientConnectionManager;

import javax.net.ssl.SSLParameters;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Http client builder for internal Vespa communications over http/https.
 *
 * Notes:
 *  - hostname verification is not enabled - CN/SAN verification is assumed to be handled by the underlying x509 trust manager.
 *  - custom connection managers must be configured through {@link #createBuilder(ConnectionManagerFactory)}. Do not call {@link HttpClientBuilder#setConnectionManager(HttpClientConnectionManager)}.
 *
 * @author bjorncs
 */
public class VespaHttpClientBuilder {

    private static final Logger log = Logger.getLogger(VespaHttpClientBuilder.class.getName());

    public interface ConnectionManagerFactory {
        HttpClientConnectionManager create(Registry<ConnectionSocketFactory> socketFactoryRegistry);
    }

    private VespaHttpClientBuilder() {}

    /**
     * Create a client builder with default connection manager.
     */
    public static HttpClientBuilder create() {
        return createBuilder(null);
    }

    /**
     * Create a client builder with a user specified connection manager.
     */
    public static HttpClientBuilder create(ConnectionManagerFactory connectionManagerFactory) {
        return createBuilder(connectionManagerFactory);
    }

    /**
     * Creates a client builder with a {@link BasicHttpClientConnectionManager} configured.
     * This connection manager uses a single connection for all requests. See Javadoc for details.
     */
    public static HttpClientBuilder createWithBasicConnectionManager() {
        return createBuilder(BasicHttpClientConnectionManager::new);
    }

    private static HttpClientBuilder createBuilder(ConnectionManagerFactory connectionManagerFactory) {
        var builder = HttpClientBuilder.create();
        addSslSocketFactory(builder, connectionManagerFactory);
        return builder;
    }

    private static void addSslSocketFactory(HttpClientBuilder builder, ConnectionManagerFactory connectionManagerFactory)  {
        TransportSecurityUtils.createTlsContext()
                .ifPresent(tlsContext -> {
                    log.log(Level.FINE, "Adding ssl socket factory to client");
                    SSLConnectionSocketFactory socketFactory = createSslSocketFactory(tlsContext);
                    if (connectionManagerFactory != null) {
                        builder.setConnectionManager(connectionManagerFactory.create(createRegistry(socketFactory)));
                    } else {
                        builder.setSSLSocketFactory(socketFactory);
                    }
                });
    }

    private static SSLConnectionSocketFactory createSslSocketFactory(TlsContext tlsContext) {
        SSLParameters parameters = tlsContext.parameters();
        return new SSLConnectionSocketFactory(tlsContext.context(), parameters.getProtocols(), parameters.getCipherSuites(), new NoopHostnameVerifier());
    }

    private static Registry<ConnectionSocketFactory> createRegistry(SSLConnectionSocketFactory sslSocketFactory) {
        return RegistryBuilder.<ConnectionSocketFactory>create()
                .register("https", sslSocketFactory)
                .register("http", getHttpSocketFactory(sslSocketFactory))
                .build();
    }

    private static ConnectionSocketFactory getHttpSocketFactory(SSLConnectionSocketFactory sslSocketFactory) {
        return TransportSecurityUtils.getInsecureMixedMode() != MixedMode.PLAINTEXT_CLIENT_MIXED_SERVER
                ? sslSocketFactory
                : PlainConnectionSocketFactory.getSocketFactory();
    }

}
