// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.searcher;

import com.yahoo.component.chain.dependencies.After;
import com.yahoo.component.chain.dependencies.Before;
import com.yahoo.container.QrSearchersConfig;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.prelude.cache.Cache;
import com.yahoo.prelude.cache.QueryCacheKey;
import com.yahoo.search.Searcher;
import com.yahoo.processing.request.CompoundName;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.statistics.Statistics;
import com.yahoo.statistics.Value;

/**
 * A generic caching searcher which caches all passing results.
 *
 * @author vegardh
 */
@After("rawQuery")
@Before("transformedQuery")
public class CachingSearcher extends Searcher {

    private static final CompoundName nocachewrite=new CompoundName("nocachewrite");

    private Cache<QueryCacheKey, Result> cache;
    private Value cacheHitRatio = null;

    public CachingSearcher(QrSearchersConfig config, Statistics manager) {
        long maxSizeBytes = config.com().yahoo().prelude().searcher().CachingSearcher().cachesizemegabytes()*1024*1024;
        long timeToLiveMillis = config.com().yahoo().prelude().searcher().CachingSearcher().timetoliveseconds()*1000;
        long maxEntrySizeBytes = config.com().yahoo().prelude().searcher().CachingSearcher().maxentrysizebytes();
        cache=new Cache<>(maxSizeBytes, timeToLiveMillis, maxEntrySizeBytes, manager);
        initRatio(manager);
    }

    private void initRatio(Statistics manager) {
        cacheHitRatio = new Value("querycache_hit_ratio", manager,
                new Value.Parameters().setNameExtension(false).setLogRaw(false).setLogMean(true));
    }

    private synchronized void cacheHit() {
        cacheHitRatio.put(1.0d);
    }

    private synchronized void cacheMiss() {
        cacheHitRatio.put(0.0d);
    }

    private boolean noCacheWrite(Query query) {
        return query.properties().getBoolean(nocachewrite);
    }

    public Result search(com.yahoo.search.Query query, Execution execution) {
        if (query.getNoCache()) {
            return execution.search(query);
        }
        QueryCacheKey queryKey = new QueryCacheKey(query);
        Result cachedResult=cache.get(queryKey);
        if (cachedResult!=null) {
            cacheHit();
            return cachedResult;
        }
        cacheMiss();
        Query originalQuery = query.clone(); // Need a copy, as cache hash key later on, maybe.
        Result result = execution.search(query);
        execution.fill(result);
        if (!noCacheWrite(query)) {
            queryKey.setQuery(originalQuery); // Because the query member has changed state
            cache.put(queryKey,result);
        }
        return result;
    }

}
