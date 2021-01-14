// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.util.http;

import com.yahoo.security.tls.MixedMode;
import com.yahoo.security.tls.TlsContext;
import com.yahoo.security.tls.TransportSecurityUtils;
import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.client5.http.impl.DefaultSchemePortResolver;
import org.apache.hc.client5.http.impl.async.HttpAsyncClientBuilder;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
import org.apache.hc.client5.http.impl.routing.DefaultRoutePlanner;
import org.apache.hc.client5.http.nio.AsyncClientConnectionManager;
import org.apache.hc.client5.http.routing.HttpRoutePlanner;
import org.apache.hc.client5.http.ssl.ClientTlsStrategyBuilder;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.nio.ssl.TlsStrategy;
import org.apache.hc.core5.http.protocol.HttpContext;

import javax.net.ssl.SSLParameters;

/**
 * Async http client builder for internal Vespa communications over http/https.
 * Configures Vespa mTLS and handles TLS mixed mode automatically.
 * Client should only be used for requests to Vespa services.
 *
 * Caveats:
 * - custom connection manager must be configured through {@link #create(AsyncConnectionManagerFactory)}.
 *
 * @author bjorncs
 */
public class VespaAsyncHttpClientBuilder {

    public interface AsyncConnectionManagerFactory {
        AsyncClientConnectionManager create(TlsStrategy tlsStrategy);
    }

    public static HttpAsyncClientBuilder create() {
        return create(
                tlsStrategy -> PoolingAsyncClientConnectionManagerBuilder.create()
                        .setTlsStrategy(tlsStrategy)
                        .build());
    }

    public static HttpAsyncClientBuilder create(AsyncConnectionManagerFactory factory) {
        HttpAsyncClientBuilder clientBuilder = HttpAsyncClientBuilder.create();
        TlsContext vespaTlsContext = TransportSecurityUtils.getSystemTlsContext().orElse(null);
        TlsStrategy tlsStrategy;
        if (vespaTlsContext != null) {
            SSLParameters vespaTlsParameters = vespaTlsContext.parameters();
            tlsStrategy = ClientTlsStrategyBuilder.create()
                    .setHostnameVerifier(new NoopHostnameVerifier())
                    .setSslContext(vespaTlsContext.context())
                    .setTlsVersions(vespaTlsParameters.getProtocols())
                    .setCiphers(vespaTlsParameters.getCipherSuites())
                    .build();
            if (TransportSecurityUtils.getInsecureMixedMode() != MixedMode.PLAINTEXT_CLIENT_MIXED_SERVER) {
                clientBuilder.setRoutePlanner(new HttpToHttpsRoutePlanner());
            }
        } else {
            tlsStrategy = ClientTlsStrategyBuilder.create().build();
        }
        clientBuilder.disableConnectionState(); // Share connections between subsequent requests
        clientBuilder.disableCookieManagement();
        clientBuilder.disableAuthCaching();
        clientBuilder.disableRedirectHandling();
        clientBuilder.setConnectionManager(factory.create(tlsStrategy));
        clientBuilder.setConnectionManagerShared(false);
        return clientBuilder;
    }

    private static class HttpToHttpsRoutePlanner implements HttpRoutePlanner {

        private final DefaultRoutePlanner defaultPlanner = new DefaultRoutePlanner(new DefaultSchemePortResolver());

        @Override
        public HttpRoute determineRoute(HttpHost target, HttpContext context) throws HttpException {
            HttpRoute originalRoute = defaultPlanner.determineRoute(target, context);
            HttpHost originalHost = originalRoute.getTargetHost();
            String originalScheme = originalHost.getSchemeName();
            String rewrittenScheme = originalScheme.equalsIgnoreCase("http") ? "https" : originalScheme;
            boolean rewrittenSecure = target.getSchemeName().equalsIgnoreCase("https");
            HttpHost rewrittenHost = new HttpHost(
                    rewrittenScheme, originalHost.getAddress(), originalHost.getHostName(), originalHost.getPort());
            return new HttpRoute(
                    rewrittenHost,
                    originalRoute.getLocalAddress(),
                    originalRoute.getProxyHost(),
                    rewrittenSecure,
                    originalRoute.getTunnelType(),
                    originalRoute.getLayerType());
        }
    }

}
