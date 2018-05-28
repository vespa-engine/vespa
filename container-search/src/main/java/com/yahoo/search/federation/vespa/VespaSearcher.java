// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.federation.vespa;

import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Map;
import java.util.Set;

import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;

import com.google.inject.Inject;
import com.yahoo.collections.Tuple2;
import com.yahoo.component.ComponentId;
import com.yahoo.component.Version;
import com.yahoo.component.chain.dependencies.After;
import com.yahoo.component.chain.dependencies.Provides;
import com.yahoo.language.Linguistics;
import com.yahoo.log.LogLevel;
import com.yahoo.prelude.query.Item;
import com.yahoo.prelude.query.QueryCanonicalizer;
import com.yahoo.processing.request.CompoundName;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.cache.QrBinaryCacheConfig;
import com.yahoo.search.cache.QrBinaryCacheRegionConfig;
import com.yahoo.search.federation.ProviderConfig;
import com.yahoo.search.federation.http.ConfiguredHTTPProviderSearcher;
import com.yahoo.search.federation.http.Connection;
import com.yahoo.search.query.QueryTree;
import com.yahoo.search.query.textserialize.TextSerialize;
import com.yahoo.search.yql.MinimalQueryInserter;
import com.yahoo.statistics.Statistics;

import edu.umd.cs.findbugs.annotations.Nullable;

/**
 * Backend searcher for external Vespa clusters (queried over http).
 *
 * <p>If the "sources" argument should be honored on an external cluster
 * when using YQL+, override {@link #chooseYqlSources(Set)}.</p>
 *
 * @author Arne Bergene Fossaa
 * @author Steinar Knutsen
 */
@Provides("Vespa")
@After("*")
public class VespaSearcher extends ConfiguredHTTPProviderSearcher {

    private final ThreadLocal<XMLReader> readerHolder = new ThreadLocal<>();
    private final Query.Type queryType;
    private final Tuple2<String, Version> segmenterVersion;

    private static final CompoundName select = new CompoundName("select");
    private static final CompoundName streamingUserid = new CompoundName("streaming.userid");
    private static final CompoundName streamingGroupname = new CompoundName("streaming.groupname");
    private static final CompoundName streamingSelection = new CompoundName("streaming.selection");

    /** Create an instance from configuration */
    public VespaSearcher(ComponentId id, ProviderConfig config, QrBinaryCacheConfig c, 
                         QrBinaryCacheRegionConfig r, Statistics statistics) {
        this(id, config, c, r, statistics, null);
    }

    /**
     * Create an instance from configuration
     *
     * @param linguistics used for generating meta info for YQL+
     */
    @Inject
    public VespaSearcher(ComponentId id, ProviderConfig config,
                         QrBinaryCacheConfig c, QrBinaryCacheRegionConfig r,
                         Statistics statistics, @Nullable Linguistics linguistics) {
        super(id, config, c, r, statistics);
        queryType = toQueryType(config.queryType());
        if (linguistics == null) {
            segmenterVersion = null;
        } else {
            segmenterVersion = linguistics.getVersion(Linguistics.Component.SEGMENTER);
        }
    }

    /**
     * Create an instance from direct parameters having a single connection.
     * Useful for testing
     */
    public VespaSearcher(String idString, String host, int port, String path) {
        super(idString, host, port, path, Statistics.nullImplementation);
        queryType = toQueryType(ProviderConfig.QueryType.LEGACY);
        segmenterVersion = null;
    }

    void addProperty(Map<String, String> queryMap, Query query, CompoundName property) {
        Object o = query.properties().get(property);
        if (o != null) {
            queryMap.put(property.toString(), o.toString());
        }
    }

    @Override
    public Map<String, String> getQueryMap(Query query) {
        Map<String, String> queryMap = getQueryMapWithoutHitsOffset(query);
        queryMap.put("offset", Integer.toString(query.getOffset()));
        queryMap.put("hits", Integer.toString(query.getHits()));
        queryMap.put("presentation.format", "xml");

        addProperty(queryMap, query, select);
        addProperty(queryMap, query, streamingUserid);
        addProperty(queryMap, query, streamingGroupname);
        addProperty(queryMap, query, streamingSelection);
        return queryMap;
    }

    @Override
    public Map<String, String> getCacheKey(Query q) {
        return getQueryMapWithoutHitsOffset(q);
    }

    private Map<String, String> getQueryMapWithoutHitsOffset(Query query) {
        Map<String, String> queryMap = super.getQueryMap(query);
        if (queryType == Query.Type.YQL) {
            queryMap.put(MinimalQueryInserter.YQL.toString(), marshalQuery(query));
        } else {
            queryMap.put("query", marshalQuery(query.getModel().getQueryTree()));
            queryMap.put("type", queryType.toString());
        }

        addNonExcludedSourceProperties(query, queryMap);
        return queryMap;
    }

    Query.Type toQueryType(ProviderConfig.QueryType.Enum providerQueryType) {
        if (providerQueryType == ProviderConfig.QueryType.LEGACY) {
            return Query.Type.ADVANCED;
        } else if (providerQueryType == ProviderConfig.QueryType.PROGRAMMATIC) {
            return Query.Type.PROGRAMMATIC;
        } else if (providerQueryType == ProviderConfig.QueryType.YQL) {
            return Query.Type.YQL;
        } else {
            throw new RuntimeException("Query type " + providerQueryType + " unsupported.");
        }
    }

    /**
     * Serialize the query parameter for outgoing queries. For YQL+ queries,
     * sources and fields will be set to all sources and all fields, to follow
     * the behavior of other query types.
     *
     * @param query
     *            the current, outgoing query
     * @return a string to include in an HTTP request
     */
    public String marshalQuery(Query query) {
        if (queryType != Query.Type.YQL) {
            return marshalQuery(query.getModel().getQueryTree());
        }

        query.getModel().getQueryTree(); // performance: parse query before cloning such that it is only done once
        Query workQuery = query.clone();
        String error = QueryCanonicalizer.canonicalize(workQuery);
        if (error != null) {
            getLogger().log(LogLevel.WARNING,"Could not normalize [" + query.toString() + "]: " + error);
            // Just returning null here is the pattern from existing code...
            return null;
        }
        chooseYqlSources(workQuery.getModel().getSources());
        chooseYqlSummaryFields(workQuery.getPresentation().getSummaryFields());
        return workQuery.yqlRepresentation(getSegmenterVersion(), false);
    }

    public String marshalQuery(QueryTree root) {
        QueryTree rootClone = root.clone(); // TODO: Why?
        String error = QueryCanonicalizer.canonicalize(rootClone);
        if (error != null) return null;

        return marshalRoot(rootClone.getRoot());
    }

    private String marshalRoot(Item root) {
        switch (queryType) {
            case ADVANCED: return new QueryMarshaller().marshal(root);
            case PROGRAMMATIC: return TextSerialize.serialize(root);
            default: throw new RuntimeException("Unsupported query type.");
        }
    }

    private XMLReader getReader() {
        XMLReader reader = readerHolder.get();
        if (reader == null) {
            reader = ResultBuilder.createParser();
            readerHolder.set(reader);
        }
        return reader;
    }

    @Override
    public void unmarshal(InputStream stream, long contentLength, Result result) {
        ResultBuilder parser = new ResultBuilder(getReader());
        Result mResult = parser.parse(new InputSource(stream),
                result.getQuery());
        result.mergeWith(mResult);
        result.hits().addAll(mResult.hits().asUnorderedHits());
    }

    /** Returns the canonical Vespa ping URI, http://host:port/status.html */
    @Override
    public URI getPingURI(Connection connection) throws MalformedURLException, URISyntaxException {
        return new URL(getParameters().getSchema(), connection.getHost(),
                connection.getPort(), "/status.html").toURI();
    }

    /**
     * Get the segmenter version data used when creating YQL queries. Useful if
     * overriding {@link #marshalQuery(Query)}.
     *
     * @return a tuple with the name of the segmenting engine in use, and its
     *         version
     */
    protected Tuple2<String, Version> getSegmenterVersion() {
        return segmenterVersion;
    }

    /**
     * Choose which source arguments to use for the external cluster when
     * generating a YQL+ query string. This is called from
     * {@link #marshalQuery(Query)}. The default implementation clears the set,
     * i.e. requests all sources. Other implementations may modify the source
     * set as they see fit, or simply do nothing.
     *
     * @param sources
     *            the set of source names to use for the outgoing query
     */
    protected void chooseYqlSources(Set<String> sources) {
        sources.clear();
    }

    /**
     * Choose which summary fields to request from the external cluster when
     * generating a YQL+ query string. This is called from
     * {@link #marshalQuery(Query)}. The default implementation clears the set,
     * i.e. requests all fields. Other implementations may modify the summary
     * field set as they see fit, or simply do nothing.
     *
     * @param summaryFields
     *            the set of source names to use for the outgoing query
     */
    protected void chooseYqlSummaryFields(Set<String> summaryFields) {
       summaryFields.clear();
    }

}
