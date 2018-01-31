// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.statistics;

import com.yahoo.component.chain.Chain;
import com.yahoo.prelude.Pong;
import com.yahoo.processing.Processor;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * A container for storing time stamps throughout the lifetime of an Execution instance.
 *
 * <p>Check state both when entering and exiting, to allow for arbitrary
 * new queries anywhere inside a search chain.
 *
 * @author Steinar Knutsen
 */
public final class TimeTracker {

    public enum Activity {
        PING,
        SEARCH,
        FILL;
    }

    static class SearcherTimer {
        // Searcher ID
        private final String name;
        // Time spent transforming query/producing result
        private final EnumMap<Activity, Long> invoking = new EnumMap<>(Activity.class);
        // Time spent transforming result
        private final EnumMap<Activity, Long> returning = new EnumMap<>(Activity.class);

        SearcherTimer(String name) {
            this.name = name;
        }

        private void activityRepr(StringBuilder buffer, int preLen,
                Map.Entry<Activity, Long> m) {
            if (buffer.length() != preLen) {
                buffer.append(", ");
            }
            buffer.append(m.getKey()).append(": ").append(m.getValue())
                    .append(" ms");
        }

        void addInvoking(Activity activity, long time) {
            Long storedTillNow = invoking.get(activity);
            long tillNow = getTime(storedTillNow);
            invoking.put(activity, Long.valueOf(tillNow + time));
        }

        void addReturning(Activity activity, long time) {
            Long storedTillNow = returning.get(activity);
            long tillNow = getTime(storedTillNow);
            returning.put(activity, Long.valueOf(tillNow + time));
        }

        Long getInvoking(Activity activity) {
            return invoking.get(activity);
        }

        String getName() {
            return name;
        }

        Long getReturning(Activity activity) {
            return returning.get(activity);
        }

        private long getTime(Long storedTillNow) {
            long tillNow;
            if (storedTillNow == null) {
                tillNow = 0L;
            } else {
                tillNow = storedTillNow.longValue();
            }
            return tillNow;
        }

        public void merge(SearcherTimer other) {
            for (Map.Entry<Activity, Long> invokingEntry : other.invoking.entrySet()) {
                addInvoking(invokingEntry.getKey(), invokingEntry.getValue());
            }
            for (Map.Entry<Activity, Long> returningEntry : other.returning.entrySet()) {
                addReturning(returningEntry.getKey(), returningEntry.getValue());
            }
        }

        public String toString() {
            StringBuilder buffer = new StringBuilder();
            int preLen;
            buffer.append(name).append("(").append("QueryProcessing(");
            preLen = buffer.length();
            for (Map.Entry<Activity, Long> m : invoking.entrySet()) {
                activityRepr(buffer, preLen, m);
            }
            buffer.append("), ResultProcessing(");
            preLen = buffer.length();
            for (Map.Entry<Activity, Long> m : returning.entrySet()) {
                activityRepr(buffer, preLen, m);
            }
            buffer.append("))");
            return buffer.toString();
        }
    }

    static class State {
        public final long start;
        public final Activity activity;

        State(long start, Activity activity) {
            super();
            this.start = start;
            this.activity = activity;
        }
    }

    static class Tag {
        public final long start;
        public final long end;
        public final Activity activity;

        Tag(long start, long end, Activity activity) {
            super();
            this.start = start;
            this.end = end;
            this.activity = activity;
        }
    }

    static class TimeSource {
        long now() {
            return System.currentTimeMillis();
        }
    }

    private State state = null;
    private List<Tag> tags = new ArrayList<>();

    private SearcherTimer[] searcherTracking = null;
    private final Chain<? extends Processor> searchChain;
    // whether the previous state was invoking or returning
    private boolean invoking = true;
    private long last = 0L;
    private final int entryIndex;
    TimeSource timeSource = new TimeSource();

    public TimeTracker(Chain<? extends Searcher> searchChain) {
        this(searchChain, 0);
    }

    public TimeTracker(Chain<? extends Processor> searchChain, int entryIndex) {
        this.searchChain = searchChain;
        this.entryIndex = entryIndex;
    }

    private void concludeState(long now) {
        if (state == null) {
            return;
        }

        tags.add(new Tag(state.start, now, state.activity));
        state = null;
    }

    private void concludeStateOnExit(long now) {
        if (now != 0L) {
            concludeState(now);
        } else {
            concludeState(getNow());
        }
    }

    private long detailedMeasurements(int searcherIndex, boolean calledAsInvoking) {
        long now = getNow();
        if (searcherTracking == null) {
            initBreakdown();
        }
        SearcherTimer timeSpentIn = getPreviouslyRunSearcher(searcherIndex, calledAsInvoking);
        long spent = now - last;
        if (timeSpentIn != null && last != 0L) {
            if (invoking) {
                timeSpentIn.addInvoking(getActivity(), spent);
            } else {
                timeSpentIn.addReturning(getActivity(), spent);
            }
        }
        last = now;
        if (searcherIndex >= searcherTracking.length) {
            // We are now outside the search chain and will go back up with the
            // default result.
            invoking = false;
        } else {
            invoking = calledAsInvoking;
        }
        return now;
    }

    private void enteringState(int searcherIndex, boolean detailed, final Activity activity) {
        long now = 0L;
        if (detailed) {
            now = detailedMeasurements(searcherIndex, true);
        }
        if (isNewState(activity)) {
            if (now == 0L) {
                now = getNow();
            }
            concludeState(now);
            initNewState(now, activity);
        }
    }

    private long fetchTime(Activity filter, Tag container) {
        if (filter == container.activity) {
            return container.end - container.start;
        } else {
            return 0L;
        }
    }

    public long fillTime() {
        return typedSum(Activity.FILL);
    }

    public long first() {
        if (tags.isEmpty()) {
            return 0L;
        } else {
            return tags.get(0).start;
        }
    }

    public long firstFill() {
        for (Tag t : tags) {
            if (t.activity == Activity.FILL) {
                return t.start;
            }
        }
        return 0L;
    }

    private Activity getActivity() {
        if (state == null) {
            throw new IllegalStateException("Trying to measure an interval having only one point.");
        }
        return state.activity;
    }

    private long getNow() {
        return timeSource.now();
    }

    private SearcherTimer getPreviouslyRunSearcher(int searcherIndex, boolean calledAsInvoking) {
        if (calledAsInvoking) {
            searcherIndex -= 1;
            if (searcherIndex < entryIndex) {
                return null;
            } else {
                return searcherTracking[searcherIndex];
            }
        } else {
            return searcherTracking[searcherIndex];
        }
    }

    private void initBreakdown() {
        if (searcherTracking != null) {
            throw new IllegalStateException("initBreakdown invoked"
                    + " when measurement structures are already initialized.");
        }
        List<? extends Processor> searchers = searchChain.components();
        searcherTracking = new SearcherTimer[searchers.size()];
        for (int i = 0; i < searcherTracking.length; ++i) {
            searcherTracking[i] = new SearcherTimer(searchers.get(i).getId().stringValue());
        }
    }

    private void initNewState(long now, Activity activity) {
        state = new State(now, activity);
    }

    void injectTimeSource(TimeSource source) {
        this.timeSource = source;
    }

    private boolean isNewState(Activity callPath) {
        if (state == null) {
            return true;
        } else if (callPath == state.activity) {
            return false;
        } else {
            return true;
        }
    }

    public long last() {
        if (tags.isEmpty()) {
            return 0L;
        } else {
            return tags.get(tags.size() - 1).end;
        }
    }

    public long pingTime() {
        return typedSum(Activity.PING);
    }

    private long returnFromState(int searcherIndex, boolean detailed) {
        if (detailed) {
            return detailedMeasurements(searcherIndex, false);
        } else {
            return 0L;
        }
    }

    public void sampleFill(int searcherIndex, boolean detailed) {
        enteringState(searcherIndex, detailed, Activity.FILL);
    }

    public void sampleFillReturn(int searcherIndex, boolean detailed, Result annotationReference) {
        ElapsedTime elapsed = getElapsedTime(annotationReference);
        sampleReturn(searcherIndex, detailed, elapsed);
    }

    public void samplePing(int searcherIndex, boolean detailed) {
        enteringState(searcherIndex, detailed, Activity.PING);
    }

    public void samplePingReturn(int searcherIndex, boolean detailed, Pong annotationReference) {
        ElapsedTime elapsed = getElapsedTime(annotationReference);
        sampleReturn(searcherIndex, detailed, elapsed);
    }

    public void sampleSearch(int searcherIndex, boolean detailed) {
        enteringState(searcherIndex, detailed, Activity.SEARCH);
    }

    public void sampleSearchReturn(int searcherIndex, boolean detailed, Result annotationReference) {
        ElapsedTime elapsed = getElapsedTime(annotationReference);
        sampleReturn(searcherIndex, detailed, elapsed);
    }

    private void sampleReturn(int searcherIndex, boolean detailed, ElapsedTime elapsed) {
        long now = returnFromState(searcherIndex, detailed);
        if (searcherIndex == entryIndex) {
            concludeStateOnExit(now);
            if (elapsed != null) {
                elapsed.add(this);
            }
        }
    }

    private ElapsedTime getElapsedTime(Result r) {
        return r == null ? null : r.getElapsedTime();
    }

    private ElapsedTime getElapsedTime(Pong p) {
        return p == null ? null : p.getElapsedTime();
    }

    SearcherTimer[] searcherTracking() {
        return searcherTracking;
    }

    public long searchTime() {
        return typedSum(Activity.SEARCH);
    }

    public long totalTime() {
        return last() - first();
    }

    private long typedSum(Activity activity) {
        long sum = 0L;
        for (Tag tag : tags) {
            sum += fetchTime(activity, tag);
        }
        return sum;
    }
}

