// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.federation.http;

import com.google.common.base.Preconditions;
import com.yahoo.search.federation.ProviderConfig.PingOption;
import org.apache.http.conn.params.ConnManagerParams;
import org.apache.http.conn.params.ConnPerRouteBean;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;

import com.yahoo.search.federation.ProviderConfig;

/**
 * A set of parameters for talking to an http backend
 *
 * @author bratseth
 * @deprecated
 */
// TODO: Remove on Vespa 7
@Deprecated // OK
public final class HTTPParameters {

    public static final String RETRIES = "com.yahoo.search.federation.http.retries";

    private boolean frozen=false;

    // All timing parameters below are in milliseconds
    /** The url request path portion */
    private String path="/";
    private int connectionTimeout=2000;
    private int readTimeout=5000;
    private boolean persistentConnections=true;
    private boolean enableProxy = false;
    private String proxyHost = "localhost";
    private int proxyPort = 1080;
    private String method = "GET";
    private String schema = "http";
    private String inputEncoding = "utf-8";
    private String outputEncoding = "utf-8";
    private int maxTotalConnections=10000;
    private int maxConnectionsPerRoute=10000;
    private int socketBufferSizeBytes=-1;
    private int retries = 1;
    private int configuredReadTimeout = -1;
    private int configuredConnectionTimeout = -1;
    private int connectionPoolTimeout = -1;
    private String certificateProxy = null;
    private int certificatePort = 0;
    private String certificateApplicationId = null;
    private boolean certificateUseProxy = false;
    private long certificateTtl = 0L;
    private long certificateRetry = 0L;

    private PingOption.Enum pingOption = PingOption.NORMAL;


    private boolean followRedirects = true;

    public HTTPParameters() {}

    public HTTPParameters(String path) {
        setPath(path);
    }

    public HTTPParameters(ProviderConfig providerConfig) {
        configuredReadTimeout = (int) (providerConfig.readTimeout() * 1000.0d);
        configuredConnectionTimeout = (int) (providerConfig.connectionTimeout() * 1000.0d);
        connectionPoolTimeout = (int) (providerConfig.connectionPoolTimeout() * 1000.0d);
        retries = providerConfig.retries();
        setPath(providerConfig.path());
        certificateUseProxy = providerConfig.yca().useProxy();
        if (certificateUseProxy) {
            certificateProxy = providerConfig.yca().host();
            certificatePort = providerConfig.yca().port();
        }
        certificateApplicationId = providerConfig.yca().applicationId();
        certificateTtl = providerConfig.yca().ttl() * 1000L;
        certificateRetry = providerConfig.yca().retry() * 1000L;
        followRedirects = providerConfig.followRedirects();
        pingOption = providerConfig.pingOption();
    }

    /**
     * Set the url path to use in queries to this. If the argument is null or empty the path is set to "/".
     * If a leading "/" is missing, it is added automatically.
     */
    public final void setPath(String path) {
        if (path==null || path.isEmpty()) path="/";

        if (! path.startsWith("/"))
            path="/" + path;
        this.path = path;
    }

    public PingOption.Enum getPingOption() {
        return pingOption;
    }

    public void setPingOption(PingOption.Enum pingOption) {
        Preconditions.checkNotNull(pingOption);
        ensureNotFrozen();
        this.pingOption = pingOption;
    }

    /** Returns the url path. Default is "/". */
    public String getPath() { return path; }

    public boolean getFollowRedirects() {
        return followRedirects;
    }

    public void setFollowRedirects(boolean followRedirects) {
        ensureNotFrozen();
        this.followRedirects = followRedirects;
    }


    public void setConnectionTimeout(int connectionTimeout) {
        ensureNotFrozen();
        this.connectionTimeout=connectionTimeout;
    }

    /** Returns the connection timeout in milliseconds. Default is 2000. */
    public int getConnectionTimeout() { return connectionTimeout; }

    public void setReadTimeout(int readTimeout) {
        ensureNotFrozen();
        this.readTimeout=readTimeout;
    }

    /** Returns the read timeout in milliseconds. Default is 5000. */
    public int getReadTimeout() { return readTimeout; }

    /**
     * <b>Note: This is currently largely a noop: Connections are reused even when this is set to true.
     * The setting will change from sharing connections between threads to only reusing it within a thread
     * but it is still reused.</b>
     */
    public void setPersistentConnections(boolean persistentConnections) {
        ensureNotFrozen();
        this.persistentConnections=persistentConnections;
    }

    /** Returns whether this should use persistent connections. Default is true. */
    public boolean getPersistentConnections() { return persistentConnections; }

    /** Returns whether proxying should be enabled. Default is false. */
    public boolean getEnableProxy() { return enableProxy; }

    public void setEnableProxy(boolean enableProxy ) {
        ensureNotFrozen();
        this.enableProxy=enableProxy;
    }

    /** Returns the proxy type to use (if enabled). Default is "http". */
    public String getProxyType() {
        return "http";
    }

    public void setProxyHost(String proxyHost) {
        ensureNotFrozen();
        this.proxyHost=proxyHost;
    }

    /** Returns the proxy host to use (if enabled). Default is "localhost". */
    public String getProxyHost() { return proxyHost; }

    public void setProxyPort(int proxyPort) {
        ensureNotFrozen();
        this.proxyPort=proxyPort;
    }

    /** Returns the proxy port to use (if enabled). Default is 1080. */
    public int getProxyPort() { return proxyPort; }

    public void setMethod(String method) {
        ensureNotFrozen();
        this.method=method;
    }

    /** Returns the http method to use. Default is "GET". */
    public String getMethod() { return method; }

    public void setSchema(String schema) {
        ensureNotFrozen();
        this.schema=schema;
    }

    /** Returns the schema to use. Default is "http". */
    public String getSchema() { return schema; }

    public void setInputEncoding(String inputEncoding) {
        ensureNotFrozen();
        this.inputEncoding=inputEncoding;
    }

    /** Returns the input encoding. Default is "utf-8". */
    public String getInputEncoding() { return inputEncoding; }

    public void setOutputEncoding(String outputEncoding) {
        ensureNotFrozen();
        this.outputEncoding=outputEncoding;
    }

    /** Returns the output encoding. Default is "utf-8". */
    public String getOutputEncoding() { return outputEncoding; }

    /** Make this unmodifiable. Note that any thread synchronization must be done outside this object. */
    public void freeze() {
        frozen=true;
    }

    private void ensureNotFrozen() {
        if (frozen) throw new IllegalStateException("Cannot modify frozen " + this);
    }

    /**
     * Returns the eligible subset of this as a HttpParams snapshot
     * AND configures the Apache HTTP library with the parameters of this
     */
    public HttpParams toHttpParams() {
        return toHttpParams(connectionTimeout, readTimeout);
    }

    /**
     * Returns the eligible subset of this as a HttpParams snapshot
     * AND configures the Apache HTTP library with the parameters of this
     */
    public HttpParams toHttpParams(int connectionTimeout, int readTimeout) {
        HttpParams params = new BasicHttpParams();
        // force use of configured value if available
        if (configuredConnectionTimeout > 0) {
            HttpConnectionParams.setConnectionTimeout(params, configuredConnectionTimeout);
        } else {
            HttpConnectionParams.setConnectionTimeout(params, connectionTimeout);
        }
        if (configuredReadTimeout > 0) {
            HttpConnectionParams.setSoTimeout(params, configuredReadTimeout);
        } else {
            HttpConnectionParams.setSoTimeout(params, readTimeout);
        }
        if (socketBufferSizeBytes > 0) {
            HttpConnectionParams.setSocketBufferSize(params, socketBufferSizeBytes);
        }
        if (connectionPoolTimeout > 0) {
            ConnManagerParams.setTimeout(params, connectionPoolTimeout);
        }
        ConnManagerParams.setMaxTotalConnections(params, maxTotalConnections);
        ConnManagerParams.setMaxConnectionsPerRoute(params, new ConnPerRouteBean(maxConnectionsPerRoute));
        if (retries >= 0) {
            params.setIntParameter(RETRIES, retries);
        }
        params.setParameter("http.protocol.handle-redirects", followRedirects);
        return params;
    }

    public int getMaxTotalConnections() {
        return maxTotalConnections;
    }

    public void setMaxTotalConnections(int maxTotalConnections) {
        ensureNotFrozen();
        this.maxTotalConnections = maxTotalConnections;
    }

    public int getMaxConnectionsPerRoute() {
        return maxConnectionsPerRoute;
    }

    public void setMaxConnectionsPerRoute(int maxConnectionsPerRoute) {
        ensureNotFrozen();
        this.maxConnectionsPerRoute = maxConnectionsPerRoute;
    }

    public int getSocketBufferSizeBytes() {
        return socketBufferSizeBytes;
    }

    public void setSocketBufferSizeBytes(int socketBufferSizeBytes) {
        ensureNotFrozen();
        this.socketBufferSizeBytes = socketBufferSizeBytes;
    }

    public int getRetries() {
        return retries;
    }

    public void setRetries(int retries) {
        ensureNotFrozen();
        this.retries = retries;
    }

    public String getYcaProxy() {
        return certificateProxy;
    }

    public int getYcaPort() {
        return certificatePort;
    }

    public String getYcaApplicationId() {
        return certificateApplicationId;
    }

    public boolean getYcaUseProxy() {
        return certificateUseProxy;
    }

    public long getYcaTtl() {
        return certificateTtl;
    }

    public long getYcaRetry() {
        return certificateRetry;
    }

}
