// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.util.http;

import com.yahoo.security.tls.MixedMode;
import com.yahoo.security.tls.TlsContext;
import com.yahoo.security.tls.TransportSecurityUtils;
import org.apache.http.HttpException;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.HttpClientConnectionManager;
import org.apache.http.conn.UnsupportedSchemeException;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.conn.routing.HttpRoutePlanner;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.BasicHttpClientConnectionManager;
import org.apache.http.impl.conn.DefaultSchemePortResolver;
import org.apache.http.protocol.HttpContext;

import javax.net.ssl.SSLParameters;
import java.net.InetAddress;
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
        HttpClientBuilder builder = HttpClientBuilder.create();
        addSslSocketFactory(builder, connectionManagerFactory);
        addHttpsRewritingRoutePlanner(builder);
        return builder;
    }

    private static void addSslSocketFactory(HttpClientBuilder builder, ConnectionManagerFactory connectionManagerFactory)  {
        TransportSecurityUtils.getSystemTlsContext()
                .ifPresent(tlsContext -> {
                    log.log(Level.FINE, "Adding ssl socket factory to client");
                    SSLConnectionSocketFactory socketFactory = createSslSocketFactory(tlsContext);
                    if (connectionManagerFactory != null) {
                        builder.setConnectionManager(connectionManagerFactory.create(createRegistry(socketFactory)));
                    } else {
                        builder.setSSLSocketFactory(socketFactory);
                    }
                    // Workaround that allows re-using https connections, see https://stackoverflow.com/a/42112034/1615280 for details.
                    // Proper solution would be to add a request interceptor that adds a x500 principal as user token,
                    // but certificate subject CN is not accessible through the TlsContext currently.
                    builder.setUserTokenHandler(context -> null);
                });
    }

    private static void addHttpsRewritingRoutePlanner(HttpClientBuilder builder) {
        if (TransportSecurityUtils.isTransportSecurityEnabled()
                && TransportSecurityUtils.getInsecureMixedMode() != MixedMode.PLAINTEXT_CLIENT_MIXED_SERVER) {
            builder.setRoutePlanner(new HttpToHttpsRoutePlanner());
        }
    }

    private static SSLConnectionSocketFactory createSslSocketFactory(TlsContext tlsContext) {
        SSLParameters parameters = tlsContext.parameters();
        return new SSLConnectionSocketFactory(tlsContext.context(), parameters.getProtocols(), parameters.getCipherSuites(), new NoopHostnameVerifier());
    }

    private static Registry<ConnectionSocketFactory> createRegistry(SSLConnectionSocketFactory sslSocketFactory) {
        return RegistryBuilder.<ConnectionSocketFactory>create()
                .register("https", sslSocketFactory)
                .register("http", PlainConnectionSocketFactory.getSocketFactory())
                .build();
    }


    /**
     * Reroutes requests using 'http' to 'https'.
     * Implementation inspired by {@link org.apache.http.impl.conn.DefaultRoutePlanner}, but without proxy support.
     */
    static class HttpToHttpsRoutePlanner implements HttpRoutePlanner {

        @Override
        public HttpRoute determineRoute(HttpHost host, HttpRequest request, HttpContext context) throws HttpException {
            HttpClientContext clientContext = HttpClientContext.adapt(context);
            RequestConfig config = clientContext.getRequestConfig();
            InetAddress local = config.getLocalAddress();

            HttpHost target = resolveTarget(host);
            boolean secure = target.getSchemeName().equalsIgnoreCase("https");
            return new HttpRoute(target, local, secure);
        }

        private HttpHost resolveTarget(HttpHost host) throws HttpException {
            try {
                String originalScheme = host.getSchemeName();
                String scheme = originalScheme.equalsIgnoreCase("http") ? "https" : originalScheme;
                int port = DefaultSchemePortResolver.INSTANCE.resolve(host);
                return new HttpHost(host.getHostName(), port, scheme);
            } catch (UnsupportedSchemeException e) {
                throw new HttpException(e.getMessage(), e);
            }
        }
    }
}
