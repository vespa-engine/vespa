// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.util.http.hc5;

import com.yahoo.security.tls.MixedMode;
import com.yahoo.security.tls.TlsContext;
import com.yahoo.security.tls.TransportSecurityUtils;
import org.apache.hc.client5.http.impl.async.HttpAsyncClientBuilder;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
import org.apache.hc.client5.http.nio.AsyncClientConnectionManager;
import org.apache.hc.client5.http.ssl.ClientTlsStrategyBuilder;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.core5.http.nio.ssl.TlsStrategy;

import javax.net.ssl.HostnameVerifier;
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
        return create(factory, new NoopHostnameVerifier());
    }

    public static HttpAsyncClientBuilder create(AsyncConnectionManagerFactory factory, HostnameVerifier hostnameVerifier) {
        HttpAsyncClientBuilder clientBuilder = HttpAsyncClientBuilder.create();
        TlsContext vespaTlsContext = TransportSecurityUtils.getSystemTlsContext().orElse(null);
        TlsStrategy tlsStrategy;
        if (vespaTlsContext != null) {
            SSLParameters vespaTlsParameters = vespaTlsContext.parameters();
            tlsStrategy = ClientTlsStrategyBuilder.create()
                    .setHostnameVerifier(hostnameVerifier)
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

}
