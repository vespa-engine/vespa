// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.util.http.hc5;

import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.io.HttpClientConnectionManager;
import org.apache.hc.client5.http.socket.ConnectionSocketFactory;
import org.apache.hc.client5.http.socket.PlainConnectionSocketFactory;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.core5.http.config.Registry;
import org.apache.hc.core5.http.config.RegistryBuilder;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLParameters;

import static com.yahoo.security.tls.MixedMode.PLAINTEXT_CLIENT_MIXED_SERVER;
import static com.yahoo.security.tls.TransportSecurityUtils.getInsecureMixedMode;
import static com.yahoo.security.tls.TransportSecurityUtils.getSystemTlsContext;
import static com.yahoo.security.tls.TransportSecurityUtils.isTransportSecurityEnabled;

/**
 * Sync HTTP client builder <em>for internal Vespa communications over http/https.</em>
 *
 * Configures Vespa mTLS and handles TLS mixed mode automatically.
 * Custom connection managers must be configured through {@link #create(HttpClientConnectionManagerFactory)}.
 *
 * @author jonmv
 */
public class VespaHttpClientBuilder {

    public interface HttpClientConnectionManagerFactory {
        HttpClientConnectionManager create(Registry<ConnectionSocketFactory> socketFactories);
    }

    public static HttpClientBuilder create() {
        return create(PoolingHttpClientConnectionManager::new);
    }

    public static HttpClientBuilder create(HttpClientConnectionManagerFactory connectionManagerFactory) {
        return create(connectionManagerFactory, new NoopHostnameVerifier());
    }

    public static HttpClientBuilder create(HttpClientConnectionManagerFactory connectionManagerFactory,
                                           HostnameVerifier hostnameVerifier) {
        return create(connectionManagerFactory, hostnameVerifier, true);
    }

    public static HttpClientBuilder create(HttpClientConnectionManagerFactory connectionManagerFactory,
                                           HostnameVerifier hostnameVerifier,
                                           boolean rewriteHttpToHttps) {
        HttpClientBuilder builder = HttpClientBuilder.create();
        addSslSocketFactory(builder, connectionManagerFactory, hostnameVerifier);
        if (rewriteHttpToHttps)
            addHttpsRewritingRoutePlanner(builder);

        builder.disableConnectionState(); // Share connections between subsequent requests.
        builder.disableCookieManagement();
        builder.disableAuthCaching();
        builder.disableRedirectHandling();

        return builder;
    }

    private static void addSslSocketFactory(HttpClientBuilder builder, HttpClientConnectionManagerFactory connectionManagerFactory,
                                            HostnameVerifier hostnameVerifier) {
        getSystemTlsContext().ifPresent(tlsContext -> {
            SSLParameters parameters = tlsContext.parameters();
            SSLConnectionSocketFactory socketFactory = new SSLConnectionSocketFactory(tlsContext.context(),
                                                                                      parameters.getProtocols(),
                                                                                      parameters.getCipherSuites(),
                                                                                      hostnameVerifier);
            builder.setConnectionManager(connectionManagerFactory.create(createRegistry(socketFactory)));
            // Workaround that allows re-using https connections, see https://stackoverflow.com/a/42112034/1615280 for details.
            // Proper solution would be to add a request interceptor that adds a x500 principal as user token,
            // but certificate subject CN is not accessible through the TlsContext currently.
            builder.setUserTokenHandler((route, context) -> null);
        });
    }

    private static Registry<ConnectionSocketFactory> createRegistry(SSLConnectionSocketFactory sslSocketFactory) {
        return RegistryBuilder.<ConnectionSocketFactory>create()
                .register("https", sslSocketFactory)
                .register("http", PlainConnectionSocketFactory.getSocketFactory())
                .build();
    }

    private static void addHttpsRewritingRoutePlanner(HttpClientBuilder builder) {
        if (isTransportSecurityEnabled() && getInsecureMixedMode() != PLAINTEXT_CLIENT_MIXED_SERVER)
            builder.setRoutePlanner(new HttpToHttpsRoutePlanner());
    }

}
