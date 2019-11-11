// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.util.http;

import com.yahoo.security.tls.MixedMode;
import com.yahoo.security.tls.TlsContext;
import com.yahoo.security.tls.TransportSecurityUtils;

import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientRequestFilter;
import javax.ws.rs.core.UriBuilder;
import java.net.URI;
import java.util.Optional;
import java.util.logging.Filter;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.util.logging.Level.CONFIG;

/**
 * Factory for JAX-RS http client builder for internal Vespa communications over http/https.
 *
 * Notes:
 *  - hostname verification is not enabled - CN/SAN verification is assumed to be handled by the underlying x509 trust manager.
 *  - ssl context or hostname verifier must not be overriden by the caller
 *
 * @author bjorncs
 */
public class VespaClientBuilderFactory implements AutoCloseable {

    private static final Logger log = Logger.getLogger(VespaClientBuilderFactory.class.getName());

    static {
        // CONFIG log message are logged repeatedly from these classes.
        disableConfigLogging("org.glassfish.jersey.client.internal.HttpUrlConnector");
        disableConfigLogging("org.glassfish.jersey.process.internal.ExecutorProviders");
    }

    // This method will hook a filter into the Jersey logger removing unwanted messages.
    private static void disableConfigLogging(String className) {
        @SuppressWarnings("LoggerInitializedWithForeignClass")
        Logger logger = Logger.getLogger(className);
        Optional<Filter> currentFilter = Optional.ofNullable(logger.getFilter());
        Filter filter = logRecord ->
                !logRecord.getMessage().startsWith("Restricted headers are not enabled")
                        && !logRecord.getMessage().startsWith("Selected ExecutorServiceProvider implementation")
                        && !logRecord.getLevel().equals(CONFIG)
                        && currentFilter.map(f -> f.isLoggable(logRecord)).orElse(true); // Honour existing filter if exists
        logger.setFilter(filter);
    }


    private final TlsContext tlsContext = TransportSecurityUtils.createTlsContext().orElse(null);
    private final MixedMode mixedMode = TransportSecurityUtils.getInsecureMixedMode();

    public ClientBuilder newBuilder() {
        ClientBuilder builder = ClientBuilder.newBuilder();
        setSslConfiguration(builder);
        return builder;
    }

    private void setSslConfiguration(ClientBuilder builder) {
        if (tlsContext != null) {
            builder.sslContext(tlsContext.context());
            builder.hostnameVerifier((hostname, sslSession) -> true); // disable hostname verification
            if (mixedMode != MixedMode.PLAINTEXT_CLIENT_MIXED_SERVER) {
                builder.register(new UriRewritingRequestFilter());
            }
        }
    }

    @Override
    public void close() {
        if (tlsContext != null) {
            tlsContext.close();
        }
    }

    static class UriRewritingRequestFilter implements ClientRequestFilter {
        @Override
        public void filter(ClientRequestContext requestContext) {
            requestContext.setUri(rewriteUri(requestContext.getUri()));
        }

        private static URI rewriteUri(URI originalUri) {
            if (!originalUri.getScheme().equals("http")) {
                return originalUri;
            }
            int port = originalUri.getPort();
            int rewrittenPort = port != -1 ? port : 80;
            URI rewrittenUri = UriBuilder.fromUri(originalUri).scheme("https").port(rewrittenPort).build();
            log.log(Level.FINE, () -> String.format("Uri rewritten from '%s' to '%s'", originalUri, rewrittenUri));
            return rewrittenUri;
        }
    }
}
