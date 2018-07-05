// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping;

import com.yahoo.component.chain.dependencies.After;
import com.yahoo.component.chain.dependencies.Before;
import com.yahoo.component.chain.dependencies.Provides;
import com.yahoo.processing.request.CompoundName;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.grouping.request.GroupingOperation;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.search.searchchain.PhaseNames;

import java.util.*;

/**
 * This searcher is responsible for turning the "select" parameter into a corresponding {@link GroupingRequest}. It will
 * also parse any "timezone" parameter as the timezone for time expressions such as {@link
 * com.yahoo.search.grouping.request.DayOfMonthFunction} and {@link com.yahoo.search.grouping.request.HourOfDayFunction}.
 *
 * @author Simon Thoresen Hult
 */
@After(PhaseNames.RAW_QUERY)
@Before(PhaseNames.TRANSFORMED_QUERY)
@Provides(GroupingQueryParser.SELECT_PARAMETER_PARSING)
public class GroupingQueryParser extends Searcher {

    public static final String SELECT_PARAMETER_PARSING = "SelectParameterParsing";
    public static final CompoundName PARAM_CONTINUE = new CompoundName("continue");
    public static final CompoundName PARAM_REQUEST = new CompoundName("select");
    public static final CompoundName PARAM_TIMEZONE = new CompoundName("timezone");
    private static final ThreadLocal<ZoneCache> zoneCache = new ThreadLocal<>();

    @Override
    public Result search(Query query, Execution execution) {
        String reqParam = query.properties().getString(PARAM_REQUEST);
        if (reqParam == null) {
            return execution.search(query);
        }
        List<Continuation> continuations = getContinuations(query.properties().getString(PARAM_CONTINUE));
        TimeZone zone = getTimeZone(query.properties().getString(PARAM_TIMEZONE, "utc"));
        for (GroupingOperation op : GroupingOperation.fromStringAsList(reqParam)) {
            GroupingRequest grpRequest = GroupingRequest.newInstance(query);
            grpRequest.setRootOperation(op);
            grpRequest.setTimeZone(zone);
            grpRequest.continuations().addAll(continuations);
        }
        return execution.search(query);
    }

    private List<Continuation> getContinuations(String param) {
        if (param == null) {
            return Collections.emptyList();
        }
        List<Continuation> ret = new LinkedList<>();
        for (String str : param.split(" ")) {
            ret.add(Continuation.fromString(str));
        }
        return ret;
    }

    private TimeZone getTimeZone(String name) {
        ZoneCache cache = zoneCache.get();
        if (cache == null) {
            cache = new ZoneCache();
            zoneCache.set(cache);
        }
        TimeZone timeZone = cache.get(name);
        if (timeZone == null) {
            timeZone = TimeZone.getTimeZone(name);
            cache.put(name, timeZone);
        }
        return timeZone;
    }

    @SuppressWarnings("serial")
    private static class ZoneCache extends LinkedHashMap<String, TimeZone> {

        ZoneCache() {
            super(16, 0.75f, true);
        }

        @Override
        protected boolean removeEldestEntry(Map.Entry<String, TimeZone> entry) {
            return size() > 128; // large enough to cache common cases
        }
    }
}
