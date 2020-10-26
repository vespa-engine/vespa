// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.logging;

import org.junit.Test;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.MatcherAssert.assertThat;

public class AccessLogSamplerTest {

    private final List<String> uris = new ArrayList<>();
    private CircularArrayAccessLogKeeper circularArrayAccessLogKeeperMock = new CircularArrayAccessLogKeeper() {
        @Override
        public void addUri(String uri) {
            uris.add(uri);
        }
    };
    private AccessLogSampler accessLogSampler = new AccessLogSampler(circularArrayAccessLogKeeperMock);

    @Test
    public void testAFewLines() {
        accessLogSampler.log(createLogEntry(200, "/search/foo"));
        accessLogSampler.log(createLogEntry(500, "/search/bar"));
        accessLogSampler.log(createLogEntry(500, "bar"));
        accessLogSampler.log(createLogEntry(200, "/search/what"));
        assertThat(uris, contains("/search/foo", "/search/what"));
    }

    @Test
    public void testSubSampling() {
        for (int i = 0; i < CircularArrayAccessLogKeeper.SIZE; i++) {
            accessLogSampler.log(createLogEntry(200, "/search/" + String.valueOf(i)));
        }
        assertThat(uris.size(), is(CircularArrayAccessLogKeeper.SIZE));
        assertThat(uris, hasItems("/search/0", "/search/1", "/search/2",
            "/search/" + String.valueOf(CircularArrayAccessLogKeeper.SIZE - 1)));
        uris.clear();
        for (int i = 0; i < CircularArrayAccessLogKeeper.SIZE; i++) {
            accessLogSampler.log(createLogEntry(200, "/search/fuzz"));
        }
        assertThat(uris, hasItem("/search/fuzz"));
        assertThat(uris.size(), is(1));
        for (int i = 0; i < CircularArrayAccessLogKeeper.SIZE; i++) {
            accessLogSampler.log(createLogEntry(200, "/search/ball"));
        }
        assertThat(uris, hasItem("/search/ball"));
        assertThat(uris.size(), is(2));
    }

    private AccessLogEntry createLogEntry(int statusCode, String uri) {
        AccessLogEntry accessLogEntry = new AccessLogEntry();
        accessLogEntry.setStatusCode(statusCode);
        accessLogEntry.setRawPath(uri);
        return accessLogEntry;
    }
}
