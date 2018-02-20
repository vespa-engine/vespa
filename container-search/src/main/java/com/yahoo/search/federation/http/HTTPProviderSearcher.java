// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.federation.http;

import com.google.common.collect.ImmutableList;
import com.yahoo.component.ComponentId;
import com.yahoo.jdisc.http.CertificateStore;
import com.yahoo.search.cache.QrBinaryCacheConfig;
import com.yahoo.search.cache.QrBinaryCacheRegionConfig;
import com.yahoo.yolean.Exceptions;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.federation.FederationSearcher;
import com.yahoo.search.query.Properties;
import com.yahoo.search.result.ErrorMessage;
import com.yahoo.search.result.Hit;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.statistics.Counter;
import com.yahoo.statistics.Statistics;
import com.yahoo.statistics.Value;

import org.apache.http.HttpEntity;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Superclass of searchers which talks to HTTP backends. Implement a subclass to talk to a backend
 * over HTTP which is not supported by the platform out of the box.
 * <p>
 * Implementations must override one of the <code>unmarshal</code> methods to unmarshal the response.
 * </p>
 *
 * @author Arne Bergene Fossaa
 * @author bratseth
 */
public abstract class HTTPProviderSearcher extends HTTPSearcher {

    private final Counter emptyResults;
    private final Value hitsPerQuery;
    private final Value responseLatency;
    private final Counter readTimeouts;

    private final static List<String> excludedSourceProperties = ImmutableList.of("offset", "hits", "provider");

    protected final static Logger log = Logger.getLogger(HTTPProviderSearcher.class.getName());

    /** The name of the cache used (which is just getid().stringValue(), or null if no cache is used */
    protected String cacheName = null;

    public HTTPProviderSearcher(ComponentId id, List<Connection> connections, String path, Statistics statistics) {
        this(id,connections,new HTTPParameters(path), statistics);
    }

    /** Creates a http provider searcher using id.getName as provider name */
    public HTTPProviderSearcher(ComponentId id, List<Connection> connections, String path,
                                Statistics statistics, CertificateStore certificateStore) {
        this(id, connections, new HTTPParameters(path), statistics, certificateStore);
    }

    public HTTPProviderSearcher(ComponentId id, List<Connection> connections, HTTPParameters parameters,
                                Statistics statistics) {
        this(id, connections, parameters, statistics, new ThrowingCertificateStore());
    }

    /**
     * Creates a provider searcher
     *
     * @param id the id of this instance
     * @param connections the connections this will load balance and fail over between
     * @param parameters the parameters to use when making http calls
     */
    public HTTPProviderSearcher(ComponentId id, List<Connection> connections, HTTPParameters parameters,
                                Statistics statistics, CertificateStore certificateStore) {
        super(id, connections, parameters, statistics, certificateStore);
        String suffix = "_" + getId().getName().replace('.', '_');
        hitsPerQuery = new Value("hits_per_query" + suffix, statistics,
                new Value.Parameters().setLogRaw(false).setNameExtension(false).setLogMean(true));
        responseLatency = new Value(LOG_LATENCY_START + suffix, statistics,
                                    new Value.Parameters().setLogRaw(false).setLogMean(true).setNameExtension(false));
        emptyResults = new Counter("empty_results" + suffix, statistics, false);
        readTimeouts = new Counter(LOG_READ_TIMEOUT_PREFIX + suffix, statistics, false);
    }

    /** @deprecated this method does nothing */
    @Deprecated
    protected void configureCache(final QrBinaryCacheConfig cacheConfig,final QrBinaryCacheRegionConfig regionConfig) {
    }

    /**
     * Unmarshal the stream by converting it to hits and adding the hits to the given result.
     * A convenience hook called by the default <code>unmarshal(entity,result).</code>
     * Override this in subclasses which does not override <code>unmarshal(entity,result).</code>
     * <p>
     * This default implementation throws an exception.
     *
     * @param stream the stream of data returned
     * @param contentLength the length of the content in bytes if known, or a negative number if unknown
     * @param result the result to which unmarshalled data should be added
     */
    public void unmarshal(final InputStream stream, long contentLength, final Result result) throws IOException {
        throw new UnsupportedOperationException("Unmarshal must be implemented by " + this);
    }

    /**
     * Unmarshal the result from an http entity. This default implementation calls
     * <code>unmarshal(entity.getContent(), entity.getContentLength(), result)</code>
     * (and does some detailed query tracing).
     *
     * @param entity the entity containing the data to unmarshal
     * @param result the result to which unmarshalled data should be added
     */
    public void unmarshal(HttpEntity entity,Result result) throws IOException {
        Query query=result.getQuery();
        long len = entity.getContentLength();
        if (query.getTraceLevel()>=4)
            query.trace("Received " + len + " bytes response in " + this, false, 4);
        query.trace("Unmarshaling result.", false, 6);
        unmarshal(entity.getContent(), len, result);

        if (query.getTraceLevel()>=2)
            query.trace("Handled " + len + " bytes response in " + this, false, 2);

    }

    protected void addNonExcludedSourceProperties(Query query, Map<String, String> queryMap) {
        Properties sourceProperties = FederationSearcher.getSourceProperties(query);
        if (sourceProperties != null) {
            for(Map.Entry<String, Object> entry : sourceProperties.listProperties("").entrySet()) {
                if (!excludedSourceProperties.contains(entry.getKey())) {
                    queryMap.put(entry.getKey(), entry.getValue().toString());
                }
            }
        }
    }

    /**
     * Hook called at the moment the result is returned from this searcher. This default implementation
     * does <code>return result</code>.
     *
     * @param result the result which is to be returned
     * @param requestMeta the request information hit, or null if none was created (e.g if this was a cache lookup)
     * @param e the exception caused during execution of this query, or null if none
     * @return the result which is returned upwards
     */
    protected Result inspectAndReturnFinalResult(Result result, Hit requestMeta, Exception e) {
        return result;
    }

    private Result statisticsBeforeInspection(Result result, Hit requestMeta, Exception e) {
        int hitCount = result.getConcreteHitCount();
        if (hitCount == 0) {
            emptyResults.increment();
        }
        hitsPerQuery.put((double) hitCount);

        if (requestMeta != null) {
            requestMeta.setField(LOG_HITCOUNT, Integer.valueOf(hitCount));
        }

        return inspectAndReturnFinalResult(result, requestMeta, e);
    }


    @Override
    protected void logResponseLatency(long latency) {
        responseLatency.put((double) latency);
    }

    @Override
    public Result search(Query query, Execution execution,Connection connection) {
        // Create default meta hit for holding logging information
        Hit requestMeta = createRequestMeta();
        Result result  = new Result(query);
        result.hits().add(requestMeta);
        query.trace("Created request information hit", false, 9);

        try {
            URI uri = getURI(query, requestMeta, connection);
            if (query.getTraceLevel()>=1)
                query.trace("Fetching " + uri.toString(), false, 1);
            long requestStartTime = System.currentTimeMillis();

            HttpEntity entity = getEntity(uri, requestMeta, query);

            // Why should consumeEntity call inspectAndReturnFinalResult itself?
            // Seems confusing to me.
            return entity == null
                    ? statisticsBeforeInspection(result, requestMeta, null)
                    : consumeEntity(entity, query, result, requestMeta, requestStartTime);

        } catch (MalformedURLException|URISyntaxException e) {
            result.hits().addError(createMalformedUrlError(query,e));
            return statisticsBeforeInspection(result, requestMeta, e);
        } catch (TimeoutException e) {
            result.hits().addError(ErrorMessage.createTimeout("No time left for HTTP traffic in "
                    + this
                    + " for " + query + ": " + e.getMessage()));
            return statisticsBeforeInspection(result, requestMeta, e);
        } catch (IOException e) {
            result.hits().addError(ErrorMessage.createBackendCommunicationError(
                    "Error when trying to connect to HTTP backend in " + this
                            + " for " + query + ": " + Exceptions.toMessageString(e)));
            return statisticsBeforeInspection(result, requestMeta, e);
        }
    }

    private Result consumeEntity(HttpEntity entity, Query query, Result result, Hit logHit, long requestStartTime) {

        try {
            // remove some time from timeout to allow for close calls with return result
            unmarshal(new TimedHttpEntity(entity, query.getStartTime(), Math.max(1, query.getTimeout() - 10)), result);
            logHit.setField(LOG_LATENCY_FINISH, System.currentTimeMillis() - requestStartTime);
            return statisticsBeforeInspection(result, logHit, null);
        } catch (IOException e) {
            result.hits().addError(ErrorMessage.createBackendCommunicationError(
                    "Error when trying to consume input in " + this + ": " + Exceptions.toMessageString(e)));
            return statisticsBeforeInspection(result, logHit, e);
        } catch (TimeoutException e) {
            readTimeouts.increment();
            result.hits().addError(ErrorMessage
                    .createTimeout("Timed out while reading/unmarshaling from backend in "
                            + this + " for " + query
                            + ": " + e.getMessage()));
            return statisticsBeforeInspection(result, logHit, e);
        } finally { // TODO: The scope of this finally must be enlarged to release the connection also on errors
            cleanupHttpEntity(entity);
        }
    }

    /**
     * Returns the key-value pairs that should be added as properties to the request url sent to the service.
     * Must be overridden in subclasses to add the key-values expected by the service in question, unless
     * {@link #getURI} (from which this is called) is overridden.
     * <p>
     * This default implementation returns the query.properties() prefixed by
     * "source.[sourceName]" or "property.[propertyName]"
     * (by calling {@link #addNonExcludedSourceProperties}).
     */
    @Override
    public Map<String,String> getQueryMap(Query query) {
        Map<String,String> queryMap = super.getQueryMap(query);
        addNonExcludedSourceProperties(query, queryMap);
        return queryMap;
    }

    /**
     * @deprecated the cache key is ignored as there is no built-in caching support
     */
    @Deprecated
    public abstract Map<String, String> getCacheKey(Query q);

}
