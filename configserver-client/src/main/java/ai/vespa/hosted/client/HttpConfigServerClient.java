// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.hosted.client;

import ai.vespa.util.http.hc5.VespaHttpClientBuilder;
import com.yahoo.vespa.athenz.api.AthenzIdentity;
import com.yahoo.vespa.athenz.tls.AthenzIdentityVerifier;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.io.SocketConfig;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSession;
import java.io.IOException;
import java.util.Collection;
import java.util.Set;

/**
 * @author jonmv
 */
public class HttpConfigServerClient extends AbstractConfigServerClient {

    private final CloseableHttpClient client;

    public HttpConfigServerClient(Collection<AthenzIdentity> serverIdentities, String userAgent) {
        if (serverIdentities.isEmpty())
            throw new IllegalArgumentException("At least one trusted server identity must be provided");

        this.client = createClient(serverIdentities, userAgent);
    }

    @Override
    public void close() throws IOException {
        client.close();
    }

    @Override
    protected ClassicHttpResponse execute(ClassicHttpRequest request, HttpClientContext context) throws IOException {
        return client.execute(request, context);
    }

    private static CloseableHttpClient createClient(Collection<AthenzIdentity> serverIdentities, String userAgent) {
        return VespaHttpClientBuilder.create(socketFactories -> {
                                                 var manager = new PoolingHttpClientConnectionManager(socketFactories);
                                                 manager.setMaxTotal(1024);
                                                 manager.setDefaultMaxPerRoute(128);
                                                 manager.setDefaultSocketConfig(SocketConfig.custom().setSoTimeout(Timeout.ofSeconds(5)).build());
                                                 manager.setValidateAfterInactivity(TimeValue.ofSeconds(10));
                                                 return manager;
                                             },
                                             new AthenzIdentityVerifier(Set.copyOf(serverIdentities)) {
                                                 @Override public boolean verify(String hostname, SSLSession session) {
                                                     return super.verify(hostname, session) || "localhost".equals(hostname);
                                                 }
                                             },
                                             false)
                                     .disableAutomaticRetries()
                                     .setUserAgent(userAgent)
                                     .build();
    }

}
