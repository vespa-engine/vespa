// Copyright 2019 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.dispatch;

import com.yahoo.prelude.fastsearch.FastHit;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.dispatch.searchcluster.Node;
import com.yahoo.search.result.Coverage;
import com.yahoo.search.result.Hit;
import com.yahoo.search.searchchain.Execution;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

class MockInvoker extends SearchInvoker {

    private final Coverage coverage;
    private final OptionalInt groupId;
    private Query query;
    private List<Hit> hits;
    int hitsRequested;

    protected MockInvoker(int key, Coverage coverage, OptionalInt groupId) {
        super(Optional.of(new Node(key, "?", 0)));
        this.coverage = coverage;
        this.groupId = groupId;
    }

    protected MockInvoker(int key, OptionalInt groupId) {
        this(key, null, groupId);
    }

    protected MockInvoker(int key, Coverage coverage) {
        this(key, coverage, OptionalInt.empty());
    }

    protected MockInvoker(int key) {
        this(key, null, OptionalInt.empty());
    }

    MockInvoker setHits(List<Hit> hits) {
        this.hits = hits;
        return this;
    }

    /** Returns the group to be invoked, if known */
    public OptionalInt groupId() { return groupId; }

    @Override
    protected Object sendSearchRequest(Query query, Object context) throws IOException {
        this.query = query;
        hitsRequested = query.getHits();
        return context;
    }

    @Override
    protected InvokerResult getSearchResult(Execution execution) throws IOException {
        InvokerResult ret = new InvokerResult(query, 10);
        if (coverage != null) {
            ret.getResult().setCoverage(coverage);
        }
        if (hits != null) {
            for (Hit h : hits) {
                if (h instanceof FastHit) {
                    FastHit fh = (FastHit) h;
                    ret.getLeanHits().add(new LeanHit(fh.getRawGlobalId(), fh.getPartId(), fh.getDistributionKey(), fh.getRelevance().getScore()));
                } else {
                    ret.getResult().hits().add(h);
                }
            }
        }
        return ret;
    }

    @Override
    protected void release() {
    }

    @Override
    public String toString() {
        return "invoker with key " + distributionKey() +
               (groupId().isPresent() ? " of group " + groupId().getAsInt() : "");
    }

}