// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.http.server;

import com.yahoo.collections.Tuple2;
import com.yahoo.container.jdisc.HttpResponse;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

/**
 * @author <a href="mailto:einarmr@yahoo-inc.com">Einar M R Rosenvinge</a>
 * @since 5.7.0
 */
public class VersionsTestCase {

    private static final List<String> EMPTY = Collections.emptyList();
    private static final List<String> ONE_TWO = Arrays.asList("1", "2");
    private static final List<String> ONE_THREE = Arrays.asList("1", "3");
    private static final List<String> TWO_THREE = Arrays.asList("3", "2");
    private static final List<String> ONE_NULL_THREE = Arrays.asList("1", null, "3");
    private static final List<String> ONE_COMMA_THREE = Collections.singletonList("1, 3");
    private static final List<String> ONE_EMPTY_THREE = Arrays.asList("1", "", "3");
    private static final List<String> TOO_LARGE_NUMBER = Collections.singletonList("1000000000");
    private static final List<String> THREE_TOO_LARGE_NUMBER = Arrays.asList("3", "1000000000");
    private static final List<String> THREE_COMMA_TOO_LARGE_NUMBER = Arrays.asList("3,1000000000");
    private static final List<String> GARBAGE = Collections.singletonList("garbage");

    @Test
    public void testEmpty() throws Exception {
        Tuple2<HttpResponse, Integer> v = FeedHandler.doCheckProtocolVersion(EMPTY);
        assertThat(v.first, instanceOf(ErrorHttpResponse.class));
        assertThat(v.second, is(-1));
    }

    @Test
    public void testOneTwo() throws Exception {
        Tuple2<HttpResponse, Integer> v = FeedHandler.doCheckProtocolVersion(ONE_TWO);
        assertThat(v.first, instanceOf(ErrorHttpResponse.class));
        assertThat(v.second, is(-1));
    }

    @Test
    public void testOneThree() throws Exception {
        Tuple2<HttpResponse, Integer> v = FeedHandler.doCheckProtocolVersion(ONE_THREE);
        assertThat(v.first, nullValue());
        assertThat(v.second, is(3));
    }

    @Test
    public void testTwoThree() throws Exception {
        Tuple2<HttpResponse, Integer> v = FeedHandler.doCheckProtocolVersion(TWO_THREE);
        assertThat(v.first, nullValue());
        assertThat(v.second, is(3));
    }

    @Test
    public void testOneNullThree() throws Exception {
        Tuple2<HttpResponse, Integer> v = FeedHandler.doCheckProtocolVersion(ONE_NULL_THREE);
        assertThat(v.first, nullValue());
        assertThat(v.second, is(3));
    }

    @Test
    public void testOneCommaThree() throws Exception {
        Tuple2<HttpResponse, Integer> v = FeedHandler.doCheckProtocolVersion(ONE_COMMA_THREE);
        assertThat(v.first, nullValue());
        assertThat(v.second, is(3));
    }

    @Test
    public void testOneEmptyThree() throws Exception {
        Tuple2<HttpResponse, Integer> v = FeedHandler.doCheckProtocolVersion(ONE_EMPTY_THREE);
        assertThat(v.first, nullValue());
        assertThat(v.second, is(3));
    }

    @Test
    public void testTooLarge() throws Exception {
        Tuple2<HttpResponse, Integer> v = FeedHandler.doCheckProtocolVersion(TOO_LARGE_NUMBER);
        assertThat(v.first, instanceOf(ErrorHttpResponse.class));
        ByteArrayOutputStream errorMsg = new ByteArrayOutputStream();
        ErrorHttpResponse errorResponse = (ErrorHttpResponse) v.first;
        errorResponse.render(errorMsg);
        assertThat(errorMsg.toString(),
                is("Could not parse X-Yahoo-Feed-Protocol-Versionheader of request (values: [1000000000]). " +
                            "Server supports protocol versions [3]"));
        assertThat(v.second, is(-1));
    }

    @Test
    public void testThreeTooLarge() throws Exception {
        Tuple2<HttpResponse, Integer> v = FeedHandler.doCheckProtocolVersion(THREE_TOO_LARGE_NUMBER);
        assertThat(v.first, nullValue());
        assertThat(v.second, is(3));
    }

    @Test
    public void testTwoCommaTooLarge() throws Exception {
        Tuple2<HttpResponse, Integer> v = FeedHandler.doCheckProtocolVersion(THREE_COMMA_TOO_LARGE_NUMBER);
        assertThat(v.first, nullValue());
        assertThat(v.second, is(3));
    }

    @Test
    public void testGarbage() throws Exception {
        Tuple2<HttpResponse, Integer> v = FeedHandler.doCheckProtocolVersion(GARBAGE);
        assertThat(v.first, instanceOf(ErrorHttpResponse.class));
        assertThat(v.second, is(-1));
    }

}
