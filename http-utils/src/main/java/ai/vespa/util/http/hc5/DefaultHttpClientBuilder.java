package ai.vespa.util.http.hc5;

import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.DefaultHostnameVerifier;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactoryBuilder;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.util.Timeout;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import java.time.Duration;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Like {@link VespaHttpClientBuilder}, but with standard TLS based on provided SSL context.
 *
 * @author jonmv
 */
public class DefaultHttpClientBuilder {

    public static final Duration connectTimeout = Duration.ofSeconds(5);
    public static final Duration socketTimeout = Duration.ofSeconds(5);

    private DefaultHttpClientBuilder() { }

    public static HttpClientBuilder create(Supplier<SSLContext> sslContext, String userAgent) {
        return create(sslContext, new DefaultHostnameVerifier(), userAgent);
    }

    /** Creates an HTTP client builder with the given SSL context, and using the provided timeouts for requests where config is not overridden. */
    public static HttpClientBuilder create(Supplier<SSLContext> sslContext, HostnameVerifier verifier, String userAgent) {
        return HttpClientBuilder.create()
                                .setConnectionManager(PoolingHttpClientConnectionManagerBuilder
                                                              .create()
                                                              .setSSLSocketFactory(SSLConnectionSocketFactoryBuilder
                                                                                           .create()
                                                                                           .setSslContext(sslContext.get())
                                                                                           .setHostnameVerifier(verifier)
                                                                                           .build())
                                                              .build())
                                .setUserAgent(userAgent)
                                .disableCookieManagement()
                                .disableAutomaticRetries()
                                .disableAuthCaching();
    }

}
