// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.util.http.hc5;

import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.client5.http.ssl.TlsSocketStrategy;
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

    private HttpClientConnectionManagerFactory connectionManagerFactory = VespaHttpClientBuilder::defaultConnectionManager;
    private HostnameVerifier hostnameVerifier = new NoopHostnameVerifier();
    private boolean rewriteHttpToHttps = true;
    private final ConnectionConfig.Builder connectionConfigBuilder = ConnectionConfig.custom();

    public interface HttpClientConnectionManagerFactory {
        PoolingHttpClientConnectionManager create(TlsSocketStrategy tlsSocketStrategy);
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
    public VespaHttpClientBuilder socketTimeout(int socketTimeout, TimeUnit timeUnit) {
        connectionConfigBuilder.setSocketTimeout(socketTimeout, timeUnit);
        return this;
    }
    public VespaHttpClientBuilder socketTimeout(Timeout socketTimeout) {
        connectionConfigBuilder.setSocketTimeout(socketTimeout);
        return this;
    }
    public VespaHttpClientBuilder validateAfterInactivity(TimeValue validateAfterInactivity) {
        connectionConfigBuilder.setValidateAfterInactivity(validateAfterInactivity);
        return this;
    }
    public VespaHttpClientBuilder timeToLive(TimeValue timeToLive) {
        connectionConfigBuilder.setTimeToLive(timeToLive);
        return this;
    }

    public HttpClientBuilder apacheBuilder() {
        HttpClientBuilder builder = HttpClientBuilder.create();
        addTlsStrategy(builder, new HttpClientConnectionManagerFactoryProxy(), hostnameVerifier);
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
        public PoolingHttpClientConnectionManager create(TlsSocketStrategy tlsSocketStrategy) {
            PoolingHttpClientConnectionManager manager = connectionManagerFactory.create(tlsSocketStrategy);
            manager.setDefaultConnectionConfig(connectionConfigBuilder.build());
            return manager;
        }
    }

    private static void addTlsStrategy(HttpClientBuilder builder, HttpClientConnectionManagerFactory connectionManagerFactory,
                                        HostnameVerifier hostnameVerifier) {
        getSystemTlsContext().ifPresent(tlsContext -> {
            TlsSocketStrategy tlsSocketStrategy = VespaTlsStrategy.of(tlsContext, hostnameVerifier);
            builder.setConnectionManager(connectionManagerFactory.create(tlsSocketStrategy));
            // Workaround that allows re-using https connections, see https://stackoverflow.com/a/42112034/1615280 for details.
            // Proper solution would be to add a request interceptor that adds a x500 principal as user token,
            // but certificate subject CN is not accessible through the TlsContext currently.
            builder.setUserTokenHandler((route, context) -> null);
        });
    }

    private static PoolingHttpClientConnectionManager defaultConnectionManager(TlsSocketStrategy tlsSocketStrategy) {
        return PoolingHttpClientConnectionManagerBuilder.create()
                .setTlsSocketStrategy(tlsSocketStrategy)
                .build();
    }

    private static void addHttpsRewritingRoutePlanner(HttpClientBuilder builder) {
        if (isTransportSecurityEnabled() && getInsecureMixedMode() != PLAINTEXT_CLIENT_MIXED_SERVER)
            builder.setRoutePlanner(new HttpToHttpsRoutePlanner());
    }

}
