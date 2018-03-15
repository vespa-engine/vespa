// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.federation.http;

import com.google.inject.Inject;
import com.yahoo.component.ComponentId;
import com.yahoo.jdisc.http.CertificateStore;
import com.yahoo.log.LogLevel;
import com.yahoo.prelude.Ping;
import com.yahoo.prelude.Pong;
import com.yahoo.yolean.Exceptions;
import com.yahoo.search.Query;
import com.yahoo.search.cluster.ClusterSearcher;
import com.yahoo.search.federation.ProviderConfig.PingOption;
import com.yahoo.search.result.ErrorMessage;
import com.yahoo.search.result.Hit;
import com.yahoo.statistics.Counter;
import com.yahoo.statistics.Statistics;
import com.yahoo.text.Utf8;

import org.apache.http.*;
import org.apache.http.client.HttpClient;
import org.apache.http.client.HttpRequestRetryHandler;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.conn.params.ConnManagerParams;
import org.apache.http.conn.params.ConnRoutePNames;
import org.apache.http.conn.routing.HttpRoutePlanner;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.DefaultHttpRoutePlanner;
import org.apache.http.impl.conn.SingleClientConnManager;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.ExecutionContext;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpRequestExecutor;
import org.apache.http.util.EntityUtils;

import javax.net.ssl.SSLHandshakeException;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.UnsupportedEncodingException;
import java.net.*;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Generic superclass of searchers making connections to some HTTP service. This
 * supports clustered connections - a list of alternative servers may be given,
 * requests will be hashed across these and failed over in case some are down.
 * <p>
 * This simply provides some utility methods for working with http connections
 * and implements ping against the service.
 *
 * <p>This searcher contains code from the Apache httpcomponents client library,
 * licensed to the Apache Software Foundation under the Apache License, Version
 * 2.0. Please refer to http://www.apache.org/licenses/LICENSE-2.0 for details.
 *
 * <p>This class automatically adds a meta hit containing latency and other
 * meta information about the obtained HTTP data using createRequestMeta().
 * The fields available in the hit are:</p>
 *
 * <dl><dt>
 * HTTPSearcher.LOG_LATENCY_START
 * <dd>
 *     The latency of the external provider answering a request.
 * <dt>
 * HTTPSearcher.LOG_LATENCY_FINISH
 * <dd>
 *     Total time of the HTTP traffic, but also decoding of the data, as this
 *     happens at the same time.
 * <dt>
 * HTTPSearcher.LOG_HITCOUNT
 * <dd>
 *     Number of concrete hits in the result returned by this provider.
 * <dt>
 * HTTPSearcher.LOG_URI
 * <dd>
 *     The complete URI used for external service.
 * <dt>
 * HTTPSearcher.LOG_SCHEME
 * <dd>
 *     The scheme of the request URI sent.
 * <dt>
 * HTTPSearcher.LOG_HOST
 * <dd>
 *     The host used for the request URI sent.
 * <dt>
 * HTTPSearcher.LOG_PORT
 * <dd>
 *     The port used for the request URI sent.
 * <dt>
 * HTTPSearcher.LOG_PATH
 * <dd>
 *     Path element of the request URI sent.
 * <dt>
 * HTTPSearcher.LOG_STATUS
 * <dd>
 *     Status code of the HTTP response.
 * <dt>
 * HTTPSearcher.LOG_PROXY_TYPE
 * <dd>
 *     The proxy type used, if any. Default is "http".
 * <dt>
 * HTTPSearcher.LOG_PROXY_HOST
 * <dd>
 *     The proxy host, if any.
 * <dt>
 * HTTPSearcher.LOG_PROXY_PORT
 * <dd>
 *     The proxy port, if any.
 * <dt>
 * HTTPSearcher.LOG_HEADER_PREFIX prepended to request header field name
 * <dd>
 *     The content of any additional request header fields.
 * <dt>
 * HTTPSearcher.LOG_RESPONSE_HEADER_PREFIX prepended to response header field name
 * <dd>
 *     The content of any additional response header fields.
 * </dl>
 *
 * @author Arne Bergene Fossaa
 */
public abstract class HTTPSearcher extends ClusterSearcher<Connection> {

    protected static final String YCA_HTTP_HEADER = "Yahoo-App-Auth";

    private static final Charset iso8859Charset = Charset.forName("ISO-8859-1");

    // Logging field name constants
    public static final String LOG_PATH = "path";
    public static final String LOG_PORT = "port";
    public static final String LOG_HOST = "host";
    public static final String LOG_IP_ADDRESS = "ip_address";
    public static final String IP_ADDRESS_UNKNOWN = "unknown";

    public static final String LOG_SCHEME = "scheme";
    public static final String LOG_URI = "uri";
    public static final String LOG_PROXY_PORT = "proxy_port";
    public static final String LOG_PROXY_HOST = "proxy_host";
    public static final String LOG_PROXY_TYPE = "proxy_type";
    public static final String LOG_STATUS = "status";
    public static final String LOG_LATENCY_FINISH = "latency_finish";
    public static final String LOG_LATENCY_START = "latency_start";
    public static final String LOG_LATENCY_CONNECT = "latency_connect";
    public static final String LOG_QUERY_PARAM_PREFIX = "query_param_";
    public static final String LOG_HEADER_PREFIX = "header_";
    public static final String LOG_RESPONSE_HEADER_PREFIX = "response_header_";
    public static final String LOG_HITCOUNT = "hit_count";
    public static final String LOG_CONNECT_TIMEOUT_PREFIX = "connect_timeout_";
    public static final String LOG_READ_TIMEOUT_PREFIX = "read_timeout_";

    protected final Logger log = Logger.getLogger(HTTPSearcher.class.getName());

    /** The HTTP parameters to use. Assigned in the constructor */
    private HTTPParameters httpParameters;

    private final Counter connectTimeouts;

    /** Whether to use certificates */
    protected boolean useCertificate = false;

    private final CertificateStore certificateStore;

    /** The (optional) certificate application ID. */
    private String certificateApplicationId = null;

    /** The (optional) certificate server proxy */
    protected HttpHost certificateProxy = null;

    /** Certificate cache TTL in ms */
    private long certificateTtl = 0L;

    /** Certificate server retry rate in the cache if no cert is found, in ms */
    private long certificateRetry = 0L;

    /** Set at construction if this is using persistent connections */
    private ClientConnectionManager sharedConnectionManager = null;

    /** Set at construction if using non-persistent connections */
    private ThreadLocal<SingleClientConnManager> singleClientConnManagerThreadLocal = null;

    private static final SchemeRegistry schemeRegistry = new SchemeRegistry();

    static {
        schemeRegistry.register(new Scheme("http", PlainSocketFactory
                .getSocketFactory(), 80));
        schemeRegistry.register(new Scheme("https", SSLSocketFactory
                .getSocketFactory(), 443));
    }

    public HTTPSearcher(ComponentId componentId, List<Connection> connections,String path, Statistics statistics) {
        this(componentId, connections, new HTTPParameters(path), statistics, new ThrowingCertificateStore());
    }

    /** Creates a http searcher with default connection and read timeouts (currently 2 and 5s respectively) */
    public HTTPSearcher(ComponentId componentId, List<Connection> connections,String path, Statistics statistics,
                        CertificateStore certificateStore) {
        this(componentId, connections, new HTTPParameters(path), statistics, certificateStore);
    }

    public HTTPSearcher(ComponentId componentId, List<Connection> connections, HTTPParameters parameters,
                        Statistics statistics) {
        this(componentId, connections, parameters, statistics, new ThrowingCertificateStore());
    }
    /**
     * Creates a http searcher
     *
     * @param componentId the id of this instance
     * @param connections the connections to establish to the backend nodes
     * @param parameters the http parameters to use. This object will be frozen if it isn't already
     */
    @Inject
    public HTTPSearcher(ComponentId componentId, List<Connection> connections, HTTPParameters parameters,
                        Statistics statistics, CertificateStore certificateStore) {
        super(componentId,connections,false);
        String suffix = "_" + getId().getName().replace('.', '_');

        connectTimeouts = new Counter(LOG_CONNECT_TIMEOUT_PREFIX + suffix, statistics, false);

        parameters.freeze();
        this.httpParameters = parameters;
        this.certificateStore = certificateStore;

        if (parameters.getPersistentConnections()) {
            HttpParams params=parameters.toHttpParams();
            HttpProtocolParams.setVersion(params, HttpVersion.HTTP_1_1);
            ConnManagerParams.setTimeout(params, 10);
            sharedConnectionManager = new ThreadSafeClientConnManager(params, schemeRegistry);
            Thread connectionPurgerThread = new Thread(() -> {
                //this is the default value in yahoo jvm installations
                long DNSTTLSec = 120;
                while (true) {
                    try {
                        Thread.sleep(DNSTTLSec * 1000);
                        if (sharedConnectionManager == null)
                            continue;

                        sharedConnectionManager.closeExpiredConnections();
                        DNSTTLSec = Long.valueOf(java.security.Security
                                .getProperty("networkaddress.cache.ttl"));
                        //No DNS TTL, no need to close idle connections
                        if (DNSTTLSec <= 0) {
                            DNSTTLSec = 120;
                            continue;
                        }
                        sharedConnectionManager.closeIdleConnections(2 * DNSTTLSec, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        return;
                    } catch (NumberFormatException e) {
                        continue;
                    }
                }
            });
            connectionPurgerThread.setDaemon(true);
            connectionPurgerThread.start();

        }
        else {
            singleClientConnManagerThreadLocal =new ThreadLocal<>();
        }

        initializeCertificate(httpParameters, certificateStore);
    }

    /**
     * Initialize certificate store and proxy if they have been set to non-null,
     * non-empty values. It will wrap thrown exceptions from the certificate store into
     * RuntimeException and propagate them.
     */
    private void initializeCertificate(HTTPParameters parameters, CertificateStore certificateStore) {
        String applicationId = parameters.getYcaApplicationId();
        String proxy = parameters.getYcaProxy();
        int port = parameters.getYcaPort();
        long ttl = parameters.getYcaTtl();
        long retry = parameters.getYcaRetry();

        if (applicationId != null && !applicationId.trim().isEmpty()) {
            initializeCertificate(applicationId, ttl, retry, certificateStore);
        }

        if (parameters.getYcaUseProxy()) {
            initializeProxy(proxy, port);
        }
    }

    /** Returns the HTTP parameters used in this. This is always frozen */
    public HTTPParameters getParameters() { return httpParameters; }

    /**
     * Returns the key-value pairs that should be added as properties to the request url sent to the service.
     * Must be overridden in subclasses to add the key-values expected by the service in question, unless
     * {@link #getURI} (from which this is called) is overridden.
     * <p>
     * This default implementation returns an empty LinkedHashMap.
     */
    public Map<String,String> getQueryMap(Query query) {
        return new LinkedHashMap<>();
    }

    /**
     * Initialize the certificate.
     * This will warn but not throw if certificates could not be loaded, as the certificates
     * are external state which can fail independently.
     */
    private void initializeCertificate(String applicationId, long ttl, long retry, CertificateStore certificateStore) {
        try {
            // get the certificate, i.e. init the cache and check integrity
            String certificate = certificateStore.getCertificate(applicationId, ttl, retry);
            if (certificate == null) {
                getLogger().log(LogLevel.WARNING, "No certificate found for application '" + applicationId + "'");
                return;
            }

            this.useCertificate = true;
            this.certificateApplicationId = applicationId;
            this.certificateTtl = ttl;
            this.certificateRetry = retry;
            getLogger().log(LogLevel.CONFIG, "Got certificate: " + certificate);
        }
        catch (Exception e) {
            getLogger().log(LogLevel.WARNING,"Exception while initializing certificate for application '" +
                                             applicationId + "' in " + this, e);
        }
    }

    /**
     * Initialize the certificate proxy setting.
     */
    private void initializeProxy(String host, int port) {
        certificateProxy = new HttpHost(host, port);
        getLogger().log(LogLevel.CONFIG, "Proxy is configured; will use proxy: " + certificateProxy);
    }

    /**
     * Same a {@code getURI(query, offset, hits, null)}.
     * @see #getURI(Query, Hit, Connection)
     */
    protected URI getURI(Query query,Connection connection) throws MalformedURLException, URISyntaxException {
        Hit requestMeta;
        try {
            requestMeta = (Hit) query.properties().get(HTTPClientSearcher.REQUEST_META_CARRIER);
        } catch (ClassCastException e) {
            requestMeta = null;
        }
        return getURI(query, requestMeta, connection);
    }

    /**
     * Creates the URI for a query.
     * Populates the {@code requestMeta} meta hit with the created URI HTTP properties.
     *
     * @param requestMeta a meta hit that holds logging information about this request (may be {@code null}).
     */
    protected URI getURI(Query query, Hit requestMeta, Connection connection)
            throws MalformedURLException, URISyntaxException {
        StringBuilder parameters = new StringBuilder();

        Map<String, String> queries = getQueryMap(query);
        if (queries.size() > 0) {
            Iterator<Map.Entry<String, String>> mapIterator = queries.entrySet().iterator();
            parameters.append("?");
            try {
                Map.Entry<String, String> entry;
                while (mapIterator.hasNext()) {
                    entry = mapIterator.next();

                    if (requestMeta != null)
                        requestMeta.setField(LOG_QUERY_PARAM_PREFIX
                                + entry.getKey(), entry.getValue());

                    parameters.append(entry.getKey() + "=" + URLEncoder.encode(entry.getValue(),
                                                                               httpParameters.getInputEncoding()));
                    if (mapIterator.hasNext()) {
                        parameters.append("&");
                    }
                }
            } catch (UnsupportedEncodingException e) {
                throw new RuntimeException("Unknown input encoding set in " + this, e);
            }
        }

        URI uri = new URL(httpParameters.getSchema(), connection.getHost(),
                          connection.getPort(), getPath() + parameters.toString()).toURI();
        if (requestMeta != null) {
            requestMeta.setField(LOG_URI, uri.toString());
            requestMeta.setField(LOG_SCHEME, uri.getScheme());
            requestMeta.setField(LOG_HOST, uri.getHost());
            requestMeta.setField(LOG_PORT, uri.getPort());
            requestMeta.setField(LOG_PATH, uri.getPath());
        }
        return uri;
    }

    /**
     * Called by getURI() to get the path of the URI for the external service.
     * The default implementation returns httpParameters.getPath(); subclasses
     * which only wants to override the path from httpParameters may use this
     * method instead of overriding all of getURI().
     *
     * @return the path to use for getURI
     */
    protected String getPath() {
        return httpParameters.getPath();
    }

    /**
     * The URI that is used to check if the provider is up or down. This will again be used in the
     * checkPing method by checking that we get a response that has a good status code (below 300). If better
     * validation than just status code checking is needed, override the checkPing method.
     */
    protected URI getPingURI(Connection connection) throws MalformedURLException, URISyntaxException {
        return new URL(httpParameters.getSchema(),connection.getHost(),connection.getPort(),getPingPath()).toURI();
    }

    /**
     * Called by getPingURI() to get the path of the URI for pinging the
     * external service. The default implementation returns
     * httpParameters.getPath(); subclasses which only wants to override the
     * path from httpParameters may use this method instead of overriding all of
     * getPingURI().
     *
     * @return the path to use for getPingURI
     */
    protected String getPingPath() {
        return httpParameters.getPath();
    }

    /**
     * Checks if the response is valid.
     * @param response The response from the ping request
     * @param pong The pong result to return back to the calling method. This method
     * will add an error to the pong result (using addError) if the status of the HTTP response is 300 or above.
     */
    protected void checkPing(HttpResponse response, Pong pong) {
        if (response.getStatusLine().getStatusCode() >= 300) {
            pong.addError(com.yahoo.search.result.ErrorMessage.createBackendCommunicationError(
                    "Got error " + response.getStatusLine().getStatusCode()
                    + " when contacting backend")
            );
        }
    }

    /**
     * Pinging in HTTPBackend is done by creating a PING uri from http://host:port/path.
     * If this returns a status that is below 300, the ping is considered good.
     *
     * If another uri is needed for pinging, reimplement getPingURI.
     *
     * Override either this method to change how ping
     */
    @Override
    public Pong ping(Ping ping, Connection connection) {
        URI uri = null;
        Pong pong = new Pong();
        HttpResponse response = null;

        if (httpParameters.getPingOption() == PingOption.DISABLE)
            return pong;

        try {
            uri = getPingURI(connection);
            if (uri == null)
                pong.addError(ErrorMessage.createIllegalQuery("Ping uri is null"));
            if (uri.getHost()==null) {
                pong.addError(ErrorMessage.createIllegalQuery("Ping uri has no host"));
                uri=null;
            }
        } catch (MalformedURLException | URISyntaxException e) {
            pong.addError(ErrorMessage.createIllegalQuery("Malformed ping uri '" + uri + "': " +
                                                          Exceptions.toMessageString(e)));
        } catch (RuntimeException e) {
            log.log(Level.WARNING,"Unexpected exception while attempting to ping " + connection + 
                                  " using uri '" + uri + "'",e);
            pong.addError(ErrorMessage.createIllegalQuery("Unexpected problem with ping uri '" + uri + "': " +
                                                          Exceptions.toMessageString(e)));
        }

        if (uri == null) return pong;
        pong.setPingInfo("using uri '" + uri + "'");

        try {
            response = getPingResponse(uri, ping);
            checkPing(response, pong);
        } catch (IOException e) {
            // We do not have a valid ping
            pong.addError(ErrorMessage.createBackendCommunicationError(
                          "Exception thrown when pinging with url '" + uri + "': " + Exceptions.toMessageString(e)));
        } catch (TimeoutException e) {
            pong.addError(ErrorMessage.createTimeout("Timeout for ping " + uri + " in " + this + ": " + e.getMessage()));
        } catch (RuntimeException e) {
            log.log(Level.WARNING,"Unexpected exception while attempting to ping " + connection + " using uri '" + uri + "'",e);
            pong.addError(ErrorMessage.createIllegalQuery("Unexpected problem with ping uri '" + uri + "': " +
                                                          Exceptions.toMessageString(e)));
        } finally {
            if (response != null) {
                cleanupHttpEntity(response.getEntity());
            }
        }

        return pong;
    }

    private HttpResponse getPingResponse(URI uri, Ping ping) throws IOException {
        long timeLeft = ping.getTimeout();
        int connectionTimeout = (int) (timeLeft / 4L);
        int readTimeout = (int) (timeLeft * 3L / 4L);

        Map<String, String> requestHeaders = null;
        if (httpParameters.getPingOption() == PingOption.YCA)
            requestHeaders = generateYCAHeaders();

        return getResponse(uri, null, requestHeaders, null, connectionTimeout, readTimeout);
    }

    /**
     * Same a {@code getEntity(uri, null)}.
     * @param uri resource to fetch
     * @param query the originating query
     * @throws TimeoutException If query.timeLeft() equal to or lower than 0
     */
    protected HttpEntity getEntity(URI uri, Query query) throws IOException{
    	return getEntity(uri, null, query);
    }


    /**
     * Gets the HTTP entity that holds the response contents.
     * @param uri the request URI.
     * @param requestMeta a meta hit that holds logging information about this request (may be {@code null}).
     * @param query the originating query
     * @return the http entity, or null if none
     * @throws java.io.IOException Whenever HTTP status code is in the 300 or higher range.
     * @throws TimeoutException If query.timeLeft() equal to or lower than 0
     */
    protected HttpEntity getEntity(URI uri, Hit requestMeta, Query query) throws IOException {
        if (query.getTimeLeft() <= 0) {
            throw new TimeoutException("No time left for querying external backend.");
        }
        HttpResponse response = getResponse(uri, requestMeta, query);
        StatusLine statusLine = response.getStatusLine();

        // Logging
        if (requestMeta != null) {
        	requestMeta.setField(LOG_STATUS, statusLine.getStatusCode());
        	for (HeaderIterator headers = response.headerIterator(); headers.hasNext(); ) {
        		Header h = headers.nextHeader();
        		requestMeta.setField(LOG_RESPONSE_HEADER_PREFIX + h.getName(), h.getValue());
        	}
        }

        if (statusLine.getStatusCode() >= 300) {
            HttpEntity entity = response.getEntity();
            String message = createServerReporterErrorMessage(statusLine, entity);
            cleanupHttpEntity(response.getEntity());
            throw new IOException(message);
        }

        return response.getEntity();
    }

    private String createServerReporterErrorMessage(StatusLine statusLine, HttpEntity entity) {
        String message = "Error when trying to connect to HTTP backend: "
                         + statusLine.getStatusCode() + " : " + statusLine.getReasonPhrase();

        try {
            if (entity != null) {
                message += "(Message = " + EntityUtils.toString(entity) + ")";
            }
        } catch (Exception e) {
            log.log(LogLevel.WARNING, "Could not get message.", e);
        }

        return message;
    }

    /**
     * Creates a meta hit dedicated to holding logging information. This hit has
     * the 'logging:[searcher's ID]' type.
     */
    protected Hit createRequestMeta() {
        Hit requestMeta = new Hit("logging:" + getId().toString());
        requestMeta.setMeta(true);
        requestMeta.types().add("logging");
        return requestMeta;
    }

    protected void cleanupHttpEntity(HttpEntity entity) {
        if (entity == null) return;

        try {
            entity.consumeContent();
        } catch (IOException e) {
            // It is ok if do not consume it, the resource will be freed after
            // timeout.
            // But log it just in case.
            log.log(LogLevel.getVespaLogLevel(LogLevel.DEBUG),
                    "Not able to consume after processing: " + Exceptions.toMessageString(e));
        }
    }

    /**
     * Same as {@code getResponse(uri, null)}.
     */
    protected HttpResponse getResponse(URI uri, Query query) throws IOException{
    	return getResponse(uri, null, query);
    }

    /**
     * Executes an HTTP request and gets the response.
     * @param uri the request URI.
     * @param requestMeta a meta hit that holds logging information about this request (may be {@code null}).
     * @param query the originating query, used to calculate timeouts
     */
    protected HttpResponse getResponse(URI uri, Hit requestMeta, Query query) throws IOException {
        long timeLeft = query.getTimeLeft();
        int connectionTimeout = (int) (timeLeft / 4L);
        int readTimeout = (int) (timeLeft * 3L / 4L);
        connectionTimeout = connectionTimeout <= 0 ? 1 : connectionTimeout;
        readTimeout = readTimeout <= 0 ? 1 : readTimeout;
        HttpEntity reqEntity = getRequestEntity(query, requestMeta);
        Map<String, String> reqHeaders = getRequestHeaders(query, requestMeta);
        if ((reqEntity == null) && (reqHeaders == null)) {
            return getResponse(uri, requestMeta, connectionTimeout, readTimeout);
        } else {
            return getResponse(uri, reqEntity, reqHeaders, requestMeta, connectionTimeout, readTimeout);
        }
    }

    /**
     * Returns the set of headers to be passed in the http request to provider backend. The default
     * implementation returns null, unless certificates are in use. If certificates are used, it will return a map
     * only containing the needed certificate headers.
     */
    protected Map<String, String> getRequestHeaders(Query query, Hit requestMeta) {
        if (useCertificate) {
            return generateYCAHeaders();
        }
        return null;
    }

 /**
     * Returns the HTTP request entity to use when making the request for this query.
     * This default implementation returns null.
     *
     * <p> Do return a repeatable entity if HTTP retry is active.
     *
     * @return the http request entity to use, or null to use the default entity
     */
    protected HttpEntity getRequestEntity(Query query, Hit requestMeta) {
        return null;
    }

    /**
     * Executes an HTTP request and gets the response.
     * @param uri the request URI.
     * @param requestMeta a meta hit that holds logging information about this request (may be {@code null}).
     * @param connectionTimeout how long to wait for getting a connection
     * @param readTimeout timeout for reading HTTP data
     */
    protected HttpResponse getResponse(URI uri, Hit requestMeta, int connectionTimeout, int readTimeout)
            throws IOException {
        return getResponse(uri, null, null, requestMeta, connectionTimeout, readTimeout);
    }


    /**
     * Executes an HTTP request and gets the response.
     * @param uri the request URI.
     * @param requestMeta a meta hit that holds logging information about this request (may be {@code null}).
     * @param connectionTimeout how long to wait for getting a connection
     * @param readTimeout timeout for reading HTTP data
     */
    protected HttpResponse getResponse(URI uri, HttpEntity reqEntity,
                                       Map<String, String> reqHeaders, Hit requestMeta,
                                       int connectionTimeout, int readTimeout) throws IOException {

        HttpParams httpParams = httpParameters.toHttpParams(connectionTimeout, readTimeout);
        HttpClient httpClient = createClient(httpParams);
        long start = 0L;
        HttpUriRequest request;
        if (httpParameters.getEnableProxy() && "http".equals(httpParameters.getProxyType())) {
            HttpHost proxy = new HttpHost(httpParameters.getProxyHost(),
                                          httpParameters.getProxyPort(), httpParameters.getProxyType());
            httpClient.getParams().setParameter(ConnRoutePNames.DEFAULT_PROXY, proxy);
            // Logging
            if (requestMeta != null) {
                requestMeta.setField(LOG_PROXY_TYPE, httpParameters.getProxyType());
                requestMeta.setField(LOG_PROXY_HOST, httpParameters.getProxyHost());
                requestMeta.setField(LOG_PROXY_PORT, httpParameters.getProxyPort());
            }
        }
        if (reqEntity == null) {
            request = createRequest(httpParameters.getMethod(), uri);
        } else {
            request = createRequest(httpParameters.getMethod(), uri, reqEntity);
        }

        if (reqHeaders != null) {
            for (Entry<String, String> entry : reqHeaders.entrySet()) {
                if (entry.getValue() == null || isAscii(entry.getValue())) {
                    request.addHeader(entry.getKey(), entry.getValue());
                } else {
                    byte[] asBytes = Utf8.toBytes(entry.getValue());
                    String asLyingString = new String(asBytes, 0, asBytes.length, iso8859Charset);
                    request.addHeader(entry.getKey(), asLyingString);
                }
            }
        }

        // Logging
        if (requestMeta != null) {
            for (HeaderIterator headers = request.headerIterator(); headers.hasNext();) {
                Header h = headers.nextHeader();
                requestMeta.setField(LOG_HEADER_PREFIX + h.getName(), h.getValue());
            }
            start = System.currentTimeMillis();
        }

        HttpResponse response;

        try {
            HttpContext context = new BasicHttpContext();
            response = httpClient.execute(request, context);

            if (requestMeta != null) {
                requestMeta.setField(LOG_IP_ADDRESS, getIpAddress(context));
            }
         } catch (ConnectTimeoutException e) {
            connectTimeouts.increment();
            throw e;
        }

        // Logging
        long latencyStart = System.currentTimeMillis() - start;
        if (requestMeta != null) {
            requestMeta.setField(LOG_LATENCY_START, latencyStart);
        }
        logResponseLatency(latencyStart);
        return response;
    }

    private String getIpAddress(HttpContext context) {
        HttpConnection connection = (HttpConnection) context.getAttribute(ExecutionContext.HTTP_CONNECTION);
        if (connection instanceof HttpInetConnection) {
            InetAddress address = ((HttpInetConnection) connection).getRemoteAddress();
            String hostAddress = address.getHostAddress();
            return hostAddress == null ?
                    IP_ADDRESS_UNKNOWN:
                    hostAddress;
        } else {
            getLogger().log(LogLevel.DEBUG, "Unexpected connection type: " + connection.getClass().getName());
            return IP_ADDRESS_UNKNOWN;
        }
    }

    private boolean isAscii(String value) {
        char[] scanBuffer = new char[value.length()];
        value.getChars(0, value.length(), scanBuffer, 0);
        for (char c: scanBuffer)
            if (c > 127) return false;
        return true;
    }

    protected void logResponseLatency(long latency) { }

    /**
     * Creates a http client for one request. Override to customize the client
     * to use, e.g for testing. This default implementation will add a certificate store
     * proxy to params if is necessary, and then do
     * <code>return new SearcherHttpClient(getConnectionManager(params), params);</code>
     */
    protected HttpClient createClient(HttpParams params) {
        if (certificateProxy != null) {
            params.setParameter(ConnRoutePNames.DEFAULT_PROXY, certificateProxy);
        }
        return new SearcherHttpClient(getConnectionManager(params), params);
    }

    /**
     * Creates a HttpRequest. Override to customize the request.
     * This default implementation does <code>return new HttpRequest(method,uri);</code>
     */
    protected HttpUriRequest createRequest(String method,URI uri) {
        return createRequest(method, uri, null);
    }

    /**
     * Creates a HttpRequest. Override to customize the request.
     * This default implementation does <code>return new HttpRequest(method,uri);</code>
     */
    protected HttpUriRequest createRequest(String method,URI uri, HttpEntity entity) {
        return new SearcherHttpRequest(method,uri);
    }

    /** Get a connection manager which may be used safely from this thread */
    protected ClientConnectionManager getConnectionManager(HttpParams params) {
        if (sharedConnectionManager != null) {// We are using shared connections
            return sharedConnectionManager;
        } else {
            SingleClientConnManager singleClientConnManager = singleClientConnManagerThreadLocal.get();
            if (singleClientConnManager == null) {
                singleClientConnManager = new SingleClientConnManager(params, schemeRegistry);
                singleClientConnManagerThreadLocal.set(singleClientConnManager);
            }
            return singleClientConnManager;
        }
    }

    /** Utility method for creating error messages when a url is incorrect */
    protected ErrorMessage createMalformedUrlError(Query query,Exception e) {
        return ErrorMessage.createErrorInPluginSearcher("Malformed url in " + this + " for " + query +
                                                        ": " + Exceptions.toMessageString(e));
    }

    private Map<String, String> generateYCAHeaders() {
        Map<String, String> headers = new HashMap<>();
        String certificate = certificateStore.getCertificate(certificateApplicationId, certificateTtl, certificateRetry);
        headers.put(YCA_HTTP_HEADER, certificate);
        return headers;
    }

    protected static class SearcherHttpClient extends DefaultHttpClient {

        private final int retries;

        public SearcherHttpClient(final ClientConnectionManager conman, final HttpParams params) {
            super(conman, params);
            retries = params.getIntParameter(HTTPParameters.RETRIES, 1);
            addRequestInterceptor((request, context) -> {
                if (!request.containsHeader("Accept-Encoding")) {
                    request.addHeader("Accept-Encoding", "gzip");
                    request.addHeader("Accept-Encoding", "br");
                }
            });
            addResponseInterceptor((response, context) -> {
                HttpEntity entity = response.getEntity();
                if (entity == null) return;
                Header ceheader = entity.getContentEncoding();
                if (ceheader == null) return;
                for (HeaderElement codec : ceheader.getElements()) {
                    if (codec.getName().equalsIgnoreCase("br")) {
                        response.setEntity(new BrotliDecompressingEntity(response.getEntity()));
                        return;
                    }
                    else if (codec.getName().equalsIgnoreCase("gzip")) {
                        response.setEntity(new GzipDecompressingEntity(response.getEntity()));
                        return;
                    }
                }
            });
        }

        @Override
        protected HttpRequestExecutor createRequestExecutor() {
            return new HttpRequestExecutor();
        }

        @Override
        protected HttpRoutePlanner createHttpRoutePlanner() {
            return new DefaultHttpRoutePlanner(getConnectionManager().getSchemeRegistry());
        }

        @Override
        protected HttpRequestRetryHandler createHttpRequestRetryHandler() {
            return new SearcherHttpRequestRetryHandler(retries);
        }
    }

    /** A retry handler which avoids retrying forever on errors misclassified as transient */
    private static class SearcherHttpRequestRetryHandler implements HttpRequestRetryHandler {
        private final int retries;

        public SearcherHttpRequestRetryHandler(int retries) {
            this.retries = retries;
        }

        @Override
        public boolean retryRequest(IOException e, int executionCount, HttpContext httpContext) {
            if (e == null) {
                throw new IllegalArgumentException("Exception parameter may not be null");
            }
            if (executionCount > retries) {
                return false;
            }
            if (e instanceof NoHttpResponseException) {
                // Retry if the server dropped connection on us
                return true;
            }
            if (e instanceof InterruptedIOException) {
                // Timeout from federation layer
                return false;
            }
            if (e instanceof UnknownHostException) {
                // Unknown host
                return false;
            }
            if (e instanceof SSLHandshakeException) {
                // SSL handshake exception
                return false;
            }
            return true;
        }


    }

    private static class SearcherHttpRequest extends HttpRequestBase {
        String method;

        public SearcherHttpRequest(String method, final URI uri) {
            super();
            this.method = method;
            setURI(uri);
        }

        @Override
        public String getMethod() {
            return method;
        }
    }

    /**
     * Only for testing.
     */
    public void shutdownConnectionManagers() {
        ClientConnectionManager manager;
        if (sharedConnectionManager != null) {
            manager = sharedConnectionManager;
        } else {
            manager = singleClientConnManagerThreadLocal.get();
        }
        if (manager != null) {
            manager.shutdown();
        }
    }

    protected static final class ThrowingCertificateStore implements CertificateStore {

        @Override
        public String getCertificate(String key, long ttl, long retry) {
            throw new UnsupportedOperationException("A certificate store is not available");
        }

    }

}

