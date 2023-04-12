// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.searchers;

import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.processing.request.CompoundName;
import com.yahoo.search.searchchain.Execution;

/**
 * Searcher that sets cache control HTTP headers in response based on query/GET parameters to
 * control caching done by proxy/caches such as YSquid and YTS:
 * <ul>
 *    <li>max-age=XXX - set with &amp;cachecontrol.maxage parameter
 *    <li>stale-while-revalidate=YYY - set with &amp;cachecontrol.staleage
 *    <li>no-cache - if Vespa &amp;noCache or &amp;cachecontrol.nocache parameter is set to true
 * </ul>
 *
 * <p>This is controlled through the three query parameters <code>cachecontrol.maxage</code>,
 * <code>cachecontrol.staleage</code> and <code>cachecontrol.nocache</code>, with the obvious meanings.</p>
 *
 * Example:
 * <ul>
 *    <li>Request: "?query=foo&amp;cachecontrol.maxage=60&amp;cachecontrol.staleage=3600"
 *    <li>Response HTTP header: "Cache-Control: max-age=60, revalidate-while-stale=3600"
 * </ul>
 *
 * Further documentation on use of Cache-Control headers:
 * http://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html#sec14.9
 *
 * @author Frode Lundgren
 */
public class CacheControlSearcher extends Searcher {

    private static final CompoundName cachecontrolNocache=CompoundName.from("cachecontrol.nocache");
    private static final CompoundName cachecontrolMaxage=CompoundName.from("cachecontrol.maxage");
    private static final CompoundName cachecontrolStaleage=CompoundName.from("cachecontrol.staleage");

    public static final String CACHE_CONTROL_HEADER = "Cache-Control";

    @Override
    public Result search(Query query, Execution execution) {
        query.trace("CacheControlSearcher: Running version $Revision$", false, 6);
        Result result = execution.search(query);
        query = result.getQuery();

        if (result.getHeaders(true) == null) {
            query.trace("CacheControlSearcher: No HTTP header map available - skipping searcher.", false, 5);
            return result;
        }

        // If you specify no-cache, no further cache control headers make sense
        if (query.properties().getBoolean(cachecontrolNocache, false) || query.getNoCache()) {
            result.getHeaders(true).put(CACHE_CONTROL_HEADER, "no-cache");
            query.trace("CacheControlSearcher: Added no-cache header", false, 4);
            return result;
        }

        // Handle max-age header
        int maxage = query.properties().getInteger(cachecontrolMaxage, -1);
        if (maxage > 0) {
            result.getHeaders(true).put(CACHE_CONTROL_HEADER, "max-age=" + maxage);
            query.trace("CacheControlSearcher: Set max-age header to " + maxage, false, 4);
        }

        // Handle stale-while-revalidate header
        int staleage = query.properties().getInteger(cachecontrolStaleage, -1);
        if (staleage > 0) {
            result.getHeaders(true).put(CACHE_CONTROL_HEADER, "stale-while-revalidate=" + staleage);
            query.trace("CacheControlSearcher: Set stale-while-revalidate header to " + staleage, false, 4);
        }

        return result;
    }
}
