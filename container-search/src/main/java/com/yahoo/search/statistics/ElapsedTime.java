// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.statistics;

import com.yahoo.collections.TinyIdentitySet;
import com.yahoo.search.statistics.TimeTracker.Activity;
import com.yahoo.search.statistics.TimeTracker.SearcherTimer;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static com.yahoo.search.statistics.TimeTracker.Activity.*;

/**
 * A collection of TimeTracker instances.
 *
 * @author Steinar Knutsen
 */
// This class may need a lot of restructuring as actual needs are mapped out.
public class ElapsedTime {

    // An identity set is used to make it safe to do multiple merges. This may happen if
    // user calls Result.mergeWith() and Result.mergeWithAfterFill() on the same result
    // with the same result as an argument too. This is slightly pathological, but better
    // safe than sorry. It also covers in SearchHandler where the same Execution instance
    // is used for search and fill.
    /** A map used as a set to store the time track of all the Execution instances for a Result */
    private Set<TimeTracker> tracks = new TinyIdentitySet<>(8);

    public void add(TimeTracker track) {
        tracks.add(track);
    }

    private long fetcher(Activity toFetch, TimeTracker fetchFrom) {
        switch (toFetch) {
            case SEARCH: return fetchFrom.searchTime();
            case FILL:   return fetchFrom.fillTime();
            case PING:   return fetchFrom.pingTime();
            default:     return 0L;
        }
    }

    /**
     * Give an estimate on how much of the time tracked by this
     * instance was used fetching document contents. This will
     * by definition be smaller than last() - first().
     */
    public long weightedFillTime() {
        return weightedTime(FILL);
    }

    private long weightedTime(Activity kind) {
        long total = 0L;
        long elapsed = 0L;
        long first = Long.MAX_VALUE;
        long last = 0L;

        if (tracks.isEmpty()) {
            return 0L;
        }
        for (TimeTracker track : tracks) {
            total += track.totalTime();
            elapsed += fetcher(kind, track);
            last = Math.max(last, track.last());
            first = Math.min(first, track.first());
        }
        if (total == 0L) {
            return 0L;
        } else {
            return ((last - first) * elapsed) / total;
        }
    }

    private long absoluteTime(Activity kind) {
        long elapsed = 0L;

        if (tracks.isEmpty()) {
            return 0L;
        }
        for (TimeTracker track : tracks) {
            elapsed += fetcher(kind, track);
        }
        return elapsed;
    }

    /**
     * Total amount of time spent in all threads for this Execution while
     * fetching document contents, or preparing to fetch them.
     */
    public long fillTime() {
        return absoluteTime(FILL);
    }

    /**
     * Total amount of time spent for this ElapsedTime instance.
     */
    public long totalTime() {
        long total = 0L;
        for (TimeTracker track : tracks) {
            total  += track.totalTime();
        }
        return total;
    }

    /**
     * Give a relative estimate on how much of the time tracked by this
     * instance was used searching. This will
     * by definition be smaller than last() - first().
     */
    public long weightedSearchTime() {
        return weightedTime(SEARCH);
    }

    /**
     * Total amount of time spent in all threads for this Execution while
     * searching or waiting for (a) backend(s) doing (a) search(es).
     */
    public long searchTime() {
        return absoluteTime(SEARCH);
    }

    /**
     * Total amount of time spent in all threads for this Execution while
     * pinging, or preparing to ping, a backend.
     */
    public long pingTime() {
        return absoluteTime(PING);
    }

    /**
     * Give a relative estimate on how much of the time tracked by this
     * instance was used pinging backends. This will
     * by definition be smaller than last() - first().
     */
    public long weightedPingTime() {
        return weightedTime(PING);
    }

    /**
     * Time stamp of start of the first event registered.
     */
    public long first() {
        long first = Long.MAX_VALUE;
        for (TimeTracker track : tracks) {
            first = Math.min(first, track.first());
        }
        return first;
    }

    /**
     * Time stamp of the end the last event registered.
     */
    public long last() {
        long last = 0L;
        for (TimeTracker track : tracks) {
            last = Math.max(last, track.last());
        }
        return last;
    }

    public void merge(ElapsedTime other) {
        for (TimeTracker t : other.tracks) {
            add(t);
        }
    }

    /**
     * The time of the start of the first document fill requested.
     */
    public long firstFill() {
        long first = Long.MAX_VALUE;
        if (tracks.isEmpty()) {
            return 0L;
        }
        for (TimeTracker t : tracks) {
            long candidate = t.firstFill();
            if (candidate == 0L) {
                continue;
            }
            first = Math.min(first, t.firstFill());
        }
        return first;
    }

    /*
     * Tell whether time use per searcher is available.
     */
    public boolean hasDetailedData() {
        for (TimeTracker t : tracks) {
            if (t.searcherTracking() != null) {
                return true;
            }
        }
        return false;
    }

    public String detailedReport() {
        Map<String, TimeTracker.SearcherTimer> raw = new LinkedHashMap<>();
        StringBuilder report = new StringBuilder();
        int preLen;
        report.append("Time use per searcher: ");
        for (TimeTracker t : tracks) {
            if (t.searcherTracking() == null) {
                continue;
            }
            SearcherTimer[] searchers = t.searcherTracking();
            for (SearcherTimer s : searchers) {
                SearcherTimer sum;
                if (raw.containsKey(s.getName())) {
                    sum = raw.get(s.getName());
                } else {
                    sum = new SearcherTimer(s.getName());
                    raw.put(s.getName(), sum);
                }
                sum.merge(s);
            }
        }
        preLen = report.length();
        for (TimeTracker.SearcherTimer value : raw.values()) {
            if (report.length() > preLen) {
                report.append(",\n    ");
            }
            report.append(value.toString());
        }
        report.append(".");
        return report.toString();
    }

}
