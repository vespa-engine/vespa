// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.prelude.fastsearch;

import com.yahoo.fs4.BasicPacket;
import com.yahoo.fs4.QueryPacket;
import com.yahoo.fs4.mplex.FS4Channel;
import com.yahoo.fs4.mplex.InvalidChannelException;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.dispatch.InterleavedSearchInvoker;
import com.yahoo.search.dispatch.MockSearchCluster;
import com.yahoo.search.dispatch.ResponseMonitor;
import com.yahoo.search.searchchain.Execution;
import org.hamcrest.Matchers;
import org.junit.Test;

import java.io.IOException;
import java.util.Collections;
import java.util.Optional;

import static org.junit.Assert.assertThat;

public class FS4SearchInvokerTestCase {
    @SuppressWarnings("resource")
    @Test
    public void testThatConnectionErrorsAreReportedImmediately() throws IOException {
        var query = new Query("?");
        query.setTimeout(1000);

        var searcher = mockSearcher();
        var cluster = new MockSearchCluster("?", 1, 1);
        var fs4invoker = new FS4SearchInvoker(searcher, query, mockFailingChannel(), Optional.empty());
        var interleave = new InterleavedSearchInvoker(Collections.singleton(fs4invoker), searcher, cluster, null);

        long start = System.currentTimeMillis();
        interleave.search(query, QueryPacket.create(null, null), null);
        long elapsed = System.currentTimeMillis() - start;

        assertThat("Connection error should fail fast", elapsed, Matchers.lessThan(500L));
    }

    private static VespaBackEndSearcher mockSearcher() {
        return new VespaBackEndSearcher() {
            @Override
            protected Result doSearch2(Query query, QueryPacket queryPacket, Execution execution) {
                return null;
            }

            @Override
            protected void doPartialFill(Result result, String summaryClass) {}
        };
    }

    private static FS4Channel mockFailingChannel() {
        return new FS4Channel() {
            @Override
            public boolean sendPacket(BasicPacket packet) throws InvalidChannelException, IOException {
                // pretend there's a connection error
                return false;
            }

            @Override
            public void setQuery(Query q) {}

            @Override
            public void setResponseMonitor(ResponseMonitor<FS4Channel> monitor) {}

            @Override
            public void close() {}
        };
    }
}
