// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.client;

import com.google.inject.Inject;
import com.ning.http.client.AsyncHandler;
import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.AsyncHttpClientConfig;
import com.ning.http.client.filter.FilterContext;
import com.ning.http.client.filter.FilterException;
import com.yahoo.jdisc.Metric;
import com.yahoo.jdisc.Request;
import com.yahoo.jdisc.handler.ContentChannel;
import com.yahoo.jdisc.handler.ResponseHandler;
import com.yahoo.jdisc.http.HttpHeaders;
import com.yahoo.jdisc.http.HttpRequest;
import com.yahoo.jdisc.http.SecretStore;
import com.yahoo.jdisc.http.client.filter.ResponseFilter;
import com.yahoo.jdisc.http.client.filter.core.ResponseFilterBridge;
import com.yahoo.jdisc.http.ssl.JKSKeyStore;
import com.yahoo.jdisc.http.ssl.SslContextFactory;
import com.yahoo.jdisc.http.ssl.SslKeyStore;
import com.yahoo.jdisc.service.AbstractClientProvider;
import com.yahoo.vespa.defaults.Defaults;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import java.net.URI;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * @author <a href="mailto:simon@yahoo-inc.com">Simon Thoresen Hult</a>
 */
public class HttpClient extends AbstractClientProvider {

    public interface Metrics {

        String NUM_REQUESTS = "clientRequests";
        String NUM_RESPONSES = "clientResponses";
        String REQUEST_LATENCY = "clientRequestLatency";
        String CONNECTION_EXCEPTIONS = "clientConnectExceptions";
        String TIMEOUT_EXCEPTIONS = "clientTimeoutExceptions";
        String OTHER_EXCEPTIONS = "clientOtherExceptions";
        String NUM_BYTES_RECEIVED = "ClientBytesReceived";
        String NUM_BYTES_SENT = "ClientBytesSent";
        String TOTAL_LATENCY = "ClientTotalResponseLatency";
        String TRANSFER_LATENCY = "ClientDataTransferLatency";
    }

    private static final String HTTP = "http";
    private static final String HTTPS = "https";

    private final ConcurrentMap<String, Metric.Context> metricCtx = new ConcurrentHashMap<>();
    private final Object metricCtxLock = new Object();
    private final AsyncHttpClient ningClient;
    private final Metric metric;
    private final boolean chunkedEncodingEnabled;

    protected HttpClient(HttpClientConfig config, ThreadFactory threadFactory, Metric metric,
                         HostnameVerifier hostnameVerifier, SSLContext sslContext,
                         List<ResponseFilter> responseFilters) {
        this.ningClient = newNingClient(config, threadFactory, hostnameVerifier, sslContext, responseFilters);
        this.metric = metric;
        this.chunkedEncodingEnabled = config.chunkedEncodingEnabled();
    }

    /** Create a client which cannot look up secrets for use in requests */
    public HttpClient(HttpClientConfig config, ThreadFactory threadFactory, Metric metric,
                      HostnameVerifier hostnameVerifier, List<ResponseFilter> responseFilters) {
        this(config, threadFactory, metric, hostnameVerifier, resolveSslContext(config.ssl(), new ThrowingSecretStore()), responseFilters);
    }

    @Inject
    public HttpClient(HttpClientConfig config, ThreadFactory threadFactory, Metric metric,
                      HostnameVerifier hostnameVerifier, List<ResponseFilter> responseFilters, SecretStore secretStore) {
        this(config, threadFactory, metric, hostnameVerifier, resolveSslContext(config.ssl(), secretStore), responseFilters);
    }

    @Override
    public ContentChannel handleRequest(Request request, ResponseHandler handler) {
        Metric.Context ctx = newMetricContext(request.getUri());
        String uriScheme = request.getUri().getScheme();

        switch (uriScheme) {
        case HTTP:
        case HTTPS:
            HttpRequest.Method method = resolveMethod(request);
            metric.add(Metrics.NUM_BYTES_SENT, request.headers().size(), ctx);

            if (!hasMessageBody(method)) {
                return EmptyRequest.executeRequest(ningClient, request, method, handler, metric, ctx);
            }
            if (isChunkedEncodingEnabled(request, method)) {
                return ChunkedRequest.executeRequest(ningClient, request, method, handler, metric, ctx);
            }
            return BufferedRequest.executeRequest(ningClient, request, method, handler, metric, ctx);
        default:
            throw new UnsupportedOperationException("Unknown protocol: " + uriScheme);
        }
    }

    @Override
    protected void destroy() {
        ningClient.close();
    }

    private HttpRequest.Method resolveMethod(Request request) {
        if (request instanceof HttpRequest) {
            return ((HttpRequest)request).getMethod();
        }
        return HttpRequest.Method.POST;
    }

    private boolean hasMessageBody(HttpRequest.Method method) {
        return method != HttpRequest.Method.TRACE;
    }

    private boolean isChunkedEncodingEnabled(Request request, HttpRequest.Method method) {
        if (!chunkedEncodingEnabled) {
            return false;
        }
        if (method == HttpRequest.Method.GET || method == HttpRequest.Method.HEAD) {
            return false;
        }
        if (request.headers().isTrue(HttpHeaders.Names.X_DISABLE_CHUNKING)) {
            return false;
        }
        if (request.headers().containsKey(HttpHeaders.Names.CONTENT_LENGTH)) {
            return false;
        }
        if (request instanceof HttpRequest && ((HttpRequest)request).getVersion() == HttpRequest.Version.HTTP_1_0) {
            return false;
        }
        return true;
    }

    private Metric.Context newMetricContext(URI uri) {
        String key = uri.getScheme() + "://" + uri.getHost() + (uri.getPort() != -1 ? ":" + uri.getPort() : "");
        Metric.Context ctx = metricCtx.get(key);
        if (ctx == null) {
            synchronized (metricCtxLock) {
                ctx = metricCtx.get(key);
                if (ctx == null) {
                    Map<String, Object> props = new HashMap<>();
                    props.put("requestUri", key);

                    ctx = metric.createContext(props);
                    if (ctx == null) {
                        ctx = NullContext.INSTANCE;
                    }
                    metricCtx.put(key, ctx);
                }
            }
        }
        if (ctx == NullContext.INSTANCE) {
            return null;
        }
        return ctx;
    }

    private static SSLContext resolveSslContext(HttpClientConfig.Ssl config, SecretStore secretStore) {
        if (!config.enabled()) {
            return null;
        }
        SslKeyStore keyStore = new JKSKeyStore(Paths.get(Defaults.getDefaults().underVespaHome(config.keyStorePath())));
        SslKeyStore trustStore = new JKSKeyStore(Paths.get(Defaults.getDefaults().underVespaHome(config.trustStorePath())));

        String password = secretStore.getSecret(config.keyDBKey());
        keyStore.setKeyStorePassword(password);
        trustStore.setKeyStorePassword(password);
        SslContextFactory sslContextFactory = SslContextFactory.newInstance(
                config.algorithm(),
                config.protocol(),
                keyStore,
                trustStore);
        return sslContextFactory.getServerSSLContext();
    }


    @SuppressWarnings("deprecation")
    private static AsyncHttpClient newNingClient(HttpClientConfig config, ThreadFactory threadFactory,
                                                 HostnameVerifier hostnameVerifier, SSLContext sslContext,
                                                 List<ResponseFilter> responseFilters) {
        AsyncHttpClientConfig.Builder builder = new AsyncHttpClientConfig.Builder();
        builder.setAllowPoolingConnection(config.connectionPoolEnabled());
        builder.setAllowSslConnectionPool(config.sslConnectionPoolEnabled());
        builder.setCompressionEnabled(config.compressionEnabled());
        builder.setConnectionTimeoutInMs((int)(config.connectionTimeout() * 1000));
        builder.setExecutorService(Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() * 2,
                                                                threadFactory));
        builder.setFollowRedirects(config.followRedirects());
        builder.setHostnameVerifier(hostnameVerifier);
        builder.setIOThreadMultiplier(2);
        builder.setIdleConnectionInPoolTimeoutInMs((int)(config.idleConnectionInPoolTimeout() * 1000));
        builder.setIdleConnectionTimeoutInMs((int)(config.idleConnectionTimeout() * 1000));
        builder.setMaxRequestRetry(config.chunkedEncodingEnabled() ? 0 : config.maxNumRetries());
        builder.setMaximumConnectionsPerHost(config.maxNumConnectionsPerHost());
        builder.setMaximumConnectionsTotal(config.maxNumConnections());
        builder.setMaximumNumberOfRedirects(config.maxNumRedirects());
        if (!config.proxyServer().isEmpty()) {
            builder.setProxyServer(ProxyServerFactory.newInstance(URI.create(config.proxyServer())));
        }
        builder.setRemoveQueryParamsOnRedirect(config.removeQueryParamsOnRedirect());
        builder.setRequestCompressionLevel(config.compressionLevel());
        builder.setRequestTimeoutInMs((int)(config.requestTimeout() * 1000));
        builder.setSSLContext(sslContext);
        builder.setUseProxyProperties(config.useProxyProperties());
        builder.setUseRawUrl(config.useRawUri());
        builder.setUserAgent(config.userAgent());
        builder.setWebSocketIdleTimeoutInMs((int)(config.idleWebSocketTimeout() * 1000));

        for (final ResponseFilter responseFilter : responseFilters) {
            builder.addResponseFilter(new com.ning.http.client.filter.ResponseFilter() {
                @Override
                @SuppressWarnings("rawtypes")
                public FilterContext filter(FilterContext filterContext) throws FilterException {
                    /*
                     * TODO: returned ResponseFilterContext is ignored right now.
                     * For now, we return the input filterContext until there is a need for custom filterContext
                     * (which will complicate the code quite a bit since we are abstracting the Ning client)
                     */
                    Request request = null;
                    AsyncHandler<?> handler = filterContext.getAsyncHandler();
                    if (handler instanceof AsyncResponseHandler) {
                        request = ((AsyncResponseHandler)handler).getRequest();
                    }
                    try {
                        // We do not retain the request here since this is executed before the response handler
                        responseFilter.filter(ResponseFilterBridge.toResponseFilterContext(filterContext, request));
                    } catch (com.yahoo.jdisc.http.client.filter.FilterException e) {
                        throw new FilterException(e.getMessage());
                    }
                    return filterContext;
                }
            }
            );
        }
        return new AsyncHttpClient(builder.build());
    }

    private static class NullContext implements Metric.Context {

        static final NullContext INSTANCE = new NullContext();
    }

    private static final class ThrowingSecretStore implements SecretStore {

        @Override
        public String getSecret(String key) {
            throw new UnsupportedOperationException("A secret store is not available");
        }

    }

}
