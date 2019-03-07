// Copyright 2019 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.dispatch;

import com.yahoo.fs4.QueryPacket;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.dispatch.searchcluster.Node;
import com.yahoo.search.result.Coverage;
import com.yahoo.search.searchchain.Execution;

import java.io.IOException;
import java.util.Optional;

class MockInvoker extends SearchInvoker {
    private final Coverage coverage;
    private Query query;

    protected MockInvoker(int key, Coverage coverage) {
        super(Optional.of(new Node(key, "?", 0, 0)));
        this.coverage = coverage;
    }

    protected MockInvoker(int key) {
        this(key, null);
    }

    @Override
    protected void sendSearchRequest(Query query, QueryPacket queryPacket) throws IOException {
        this.query = query;
    }

    @Override
    protected Result getSearchResult(Execution execution) throws IOException {
        Result ret = new Result(query);
        if (coverage != null) {
            ret.setCoverage(coverage);
        }
        return ret;
    }

    @Override
    protected void release() {
    }
}