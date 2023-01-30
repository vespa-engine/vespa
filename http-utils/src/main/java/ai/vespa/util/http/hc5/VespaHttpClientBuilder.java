// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.util.http.hc5;

import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.socket.ConnectionSocketFactory;
import org.apache.hc.client5.http.socket.PlainConnectionSocketFactory;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.core5.http.config.Registry;
import org.apache.hc.core5.http.config.RegistryBuilder;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;

import javax.net.ssl.HostnameVerifier;

import java.util.concurrent.TimeUnit;

import static com.yahoo.security.tls.MixedMode.PLAINTEXT_CLIENT_MIXED_SERVER;
import static com.yahoo.security.tls.TransportSecurityUtils.getInsecureMixedMode;
import static com.yahoo.security.tls.TransportSecurityUtils.getSystemTlsContext;
import static com.yahoo.security.tls.TransportSecurityUtils.isTransportSecurityEnabled;

/**
 * Sync HTTP client builder <em>for internal Vespa communications over http/https.</em>
 * Configures Vespa mTLS and handles TLS mixed mode automatically.
 * Custom connection managers must be configured through {@link #connectionManagerFactory(HttpClientConnectionManagerFactory)}.
 *
 * @author jonmv
 */
public class VespaHttpClientBuilder {

    private HttpClientConnectionManagerFactory connectionManagerFactory = PoolingHttpClientConnectionManager::new;
    private HostnameVerifier hostnameVerifier = new NoopHostnameVerifier();
    private boolean rewriteHttpToHttps = true;
    private final ConnectionConfig.Builder connectionConfigBuilder = ConnectionConfig.custom();

    public interface HttpClientConnectionManagerFactory {
        PoolingHttpClientConnectionManager create(Registry<ConnectionSocketFactory> socketFactories);
    }

    private VespaHttpClientBuilder() {
    }

    public static VespaHttpClientBuilder custom() {
        return new VespaHttpClientBuilder();
    }

    public VespaHttpClientBuilder connectionManagerFactory(HttpClientConnectionManagerFactory connectionManagerFactory) {
        this.connectionManagerFactory = connectionManagerFactory;
        return this;
    }

    public VespaHttpClientBuilder hostnameVerifier(HostnameVerifier hostnameVerifier) {
        this.hostnameVerifier = hostnameVerifier;
        return this;
    }
    public VespaHttpClientBuilder rewriteHttpToHttps(boolean enable) {
        this.rewriteHttpToHttps = enable;
        return this;
    }
    public VespaHttpClientBuilder connectTimeout(long connectTimeout, TimeUnit timeUnit) {
        connectionConfigBuilder.setConnectTimeout(connectTimeout, timeUnit);
        return this;
    }
    public VespaHttpClientBuilder connectTimeout(Timeout connectTimeout) {
        connectionConfigBuilder.setConnectTimeout(connectTimeout);
        return this;
    }
    public VespaHttpClientBuilder socketTimeout(long connectTimeout, TimeUnit timeUnit) {
        connectionConfigBuilder.setConnectTimeout(connectTimeout, timeUnit);
        return this;
    }
    public VespaHttpClientBuilder validateAfterInactivity(TimeValue validateAfterInactivity) {
        connectionConfigBuilder.setValidateAfterInactivity(validateAfterInactivity);
        return this;
    }
    public VespaHttpClientBuilder socketTimeout(Timeout connectTimeout) {
        connectionConfigBuilder.setConnectTimeout(connectTimeout);
        return this;
    }

    public HttpClientBuilder apacheBuilder() {
        HttpClientBuilder builder = HttpClientBuilder.create();
        addSslSocketFactory(builder, new HttpClientConnectionManagerFactoryProxy(), hostnameVerifier);
        if (rewriteHttpToHttps)
            addHttpsRewritingRoutePlanner(builder);

        builder.disableConnectionState(); // Share connections between subsequent requests.
        builder.disableCookieManagement();
        builder.disableAuthCaching();
        builder.disableRedirectHandling();

        return builder;
    }
    public CloseableHttpClient buildClient() {
        return apacheBuilder().build();
    }

    private class HttpClientConnectionManagerFactoryProxy implements HttpClientConnectionManagerFactory {
        @Override
        public PoolingHttpClientConnectionManager create(Registry<ConnectionSocketFactory> socketFactories) {
            PoolingHttpClientConnectionManager manager = connectionManagerFactory.create(socketFactories);
            manager.setDefaultConnectionConfig(connectionConfigBuilder.build());
            return manager;
        }
    }

    private static void addSslSocketFactory(HttpClientBuilder builder, HttpClientConnectionManagerFactory connectionManagerFactory,
                                            HostnameVerifier hostnameVerifier) {
        getSystemTlsContext().ifPresent(tlsContext -> {
            SSLConnectionSocketFactory socketFactory = SslConnectionSocketFactory.of(tlsContext, hostnameVerifier);
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
