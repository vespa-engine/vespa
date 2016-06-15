// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.federation.http;

import com.yahoo.component.ComponentId;
import com.yahoo.jdisc.http.CertificateStore;
import com.yahoo.yolean.Exceptions;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.processing.request.CompoundName;
import com.yahoo.search.result.ErrorMessage;
import com.yahoo.search.result.Hit;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.statistics.Statistics;

import org.apache.http.HttpEntity;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * A utility parent for searchers which gets data from web services which is incorporated into the query.
 * This searcher will take care of implementing the search method while the extending class implements
 * {@link #getQueryMap} and {@link #handleResponse} to create the http request and handle the response, respectively.
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
 *     Total time of the HTTP traffic, but also decoding of the data, is this
 *     happens at the same time.
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

 * @author <a href="mailto:arnebef@yahoo-inc.com">Arne Bergene Fossaa</a>
 * @author bratseth
 */
public abstract class HTTPClientSearcher extends HTTPSearcher {

    static final CompoundName REQUEST_META_CARRIER = new CompoundName("com.yahoo.search.federation.http.HTTPClientSearcher_requestMeta");

    protected final static Logger log = Logger.getLogger(HTTPClientSearcher.class.getName());

    /**
     * Creates a client searcher
     *
     * @param id the id of this instance
     * @param connections the connections this will load balance and fail over between
     * @param path the path portion of the url to be used
     */
    public HTTPClientSearcher(ComponentId id, List<Connection> connections,String path,Statistics statistics) {
        super(id, connections, path, statistics);
    }

    public HTTPClientSearcher(ComponentId id, List<Connection> connections,String path,Statistics statistics,
                              CertificateStore certificateStore) {
        super(id, connections, path, statistics, certificateStore);
    }

    public HTTPClientSearcher(ComponentId id, List<Connection> connections, HTTPParameters parameters, Statistics statistics) {
        super(id, connections, parameters, statistics);
    }
    /**
     * Creates a client searcher
     *
     * @param id the id of this instance
     * @param connections the connections this will load balance and fail over between
     * @param parameters the parameters to use when making http calls
     * @param certificateStore the certificate store to use to pass certificates in requests
     */
    public HTTPClientSearcher(ComponentId id, List<Connection> connections, HTTPParameters parameters,
                              Statistics statistics, CertificateStore certificateStore) {
        super(id, connections, parameters, statistics, certificateStore);
    }

    /** Overridden to avoid interfering with errors from nested searchers, which is inappropriate for a <i>client</i> */
    @Override
    public Result robustSearch(Query query, Execution execution, Connection connection) {
        return search(query,execution,connection);
    }

    /** Implements a search towards the connection chosen by the cluster searcher for this query */
    @Override
    public Result search(Query query, Execution execution, Connection connection) {
        Hit requestMeta = doHttpRequest(query, connection);
        Result result = execution.search(query);
        result.hits().add(requestMeta);
        return result;
    }

    private Hit doHttpRequest(Query query, Connection connection) {
        URI uri;
        // Create default meta hit for holding logging information
        Hit requestMeta = createRequestMeta();
        query.properties().set(REQUEST_META_CARRIER, requestMeta);

        query.trace("Created request information hit",false,9);
        try {
            uri = getURI(query, connection);
        } catch (MalformedURLException e) {
            query.errors().add(createMalformedUrlError(query,e));
            return requestMeta;
        } catch (URISyntaxException e) {
            query.errors().add(createMalformedUrlError(query,e));
            return requestMeta;
        }

        HttpEntity entity;
        try {
            if (query.getTraceLevel()>=1)
                query.trace("Fetching " + uri.toString(), false, 1);
            entity = getEntity(uri, requestMeta, query);
        } catch (IOException e) {
            query.errors().add(ErrorMessage.createBackendCommunicationError(
                    "Error when trying to connect to HTTP backend in " + this + " using " + connection + " for " +
                    query + ": " + Exceptions.toMessageString(e)));
            return requestMeta;
        } catch (TimeoutException e) {
            query.errors().add(ErrorMessage.createTimeout("HTTP traffic timed out in "
                    + this + " for " + query + ": " + e.getMessage()));
            return requestMeta;
        }
        if (entity==null) {
            query.errors().add(ErrorMessage.createBackendCommunicationError(
                    "No result from connecting to HTTP backend in " + this + " using " + connection + " for " +  query));
            return requestMeta;
        }

        try {
            query = handleResponse(entity,query);
        }
        catch (IOException e) {
            query.errors().add(ErrorMessage.createBackendCommunicationError(
                    "Error when trying to consume input in " + this + ": " + Exceptions.toMessageString(e)));
        } finally {
            cleanupHttpEntity(entity);
        }
        return requestMeta;
    }

    /** Overrides to pass the query on to the next searcher */
    @Override
    public Result search(Query query, Execution execution, ErrorMessage error) {
        query.errors().add(error);
        return execution.search(query);
    }

    /** Do nothing on fill in client searchers */
    @Override
    public void fill(Result result,String summaryClass,Execution execution,Connection connection) {
    }

    /**
     * Convenience hook for unmarshalling the response and adding the information to the query.
     * Implement this or <code>handleResponse(entity,query)</code> in any subclass.
     * This default implementation throws an exception.
     *
     * @param inputStream the stream containing the data from the http service
     * @param contentLength the length of the content in the stream in bytes, or a negative number if not known
     * @param query the current query, to which information from the stream should be added
     * @return query the query to propagate down the chain. This should almost always be the
     *         query instance given as a parameter.
     */
    public Query handleResponse(InputStream inputStream, long contentLength, Query query) throws IOException {
        throw new UnsupportedOperationException("handleResponse must be implemented by " + this);
    }

    /**
     * Unmarshals the response and adds the resulting data to the given query.
     * This default implementation calls
     * <code>return handleResponse(entity.getContent(), entity.getContentLength(), query);</code>
     * (and does some detailed query tracing).
     *
     * @param query the current query, to which information from the stream should be added
     * @return query the query to propagate down the chain. This should almost always be the
     *         query instance given as a parameter.
     */
    public Query handleResponse(HttpEntity entity, Query query) throws IOException {
        long len = entity.getContentLength();
        if (query.getTraceLevel()>=4)
            query.trace("Received " + len + " bytes response in " + this, false, 4);
        query = handleResponse(entity.getContent(), len, query);
        if (query.getTraceLevel()>=2)
            query.trace("Handled " + len + " bytes response in " + this, false, 2);
        return query;
    }

    /** Never retry individual queries to clients for now */
    @Override
    protected boolean shouldRetry(Query query,Result result) { return false; }

    /**
     * numHits and offset should not be part of the cache key as cache supports
     * partial read/write that is only one cache entry is maintained per query
     * irrespective of the offset and numhits.
     */
    public abstract Map<String, String> getCacheKey(Query q);

    /**
     * Adds all key-values starting by "service." + getClientName() in query.properties().
     * Returns the empty map if {@link #getServiceName} is not overridden.
     */
    @Override
    public Map<String,String> getQueryMap(Query query) {
        LinkedHashMap<String, String> queryMap=new LinkedHashMap<>();
        if (getServiceName().isEmpty()) return queryMap;

        for (Map.Entry<String,Object> objectProperty : query.properties().listProperties("service." + getServiceName()).entrySet()) // TODO: Make more efficient using CompoundName
            queryMap.put(objectProperty.getKey(),objectProperty.getValue().toString());
        return queryMap;
    }

    /**
     * Override this to return the name of the service this is a client of.
     * This is used to look up service specific properties as service.getServiceName.serviceSpecificProperty.
     * This default implementation returns "", which means service specific parameters will not be used.
     */
    protected String getServiceName() { return ""; }

}
