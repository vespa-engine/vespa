// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.http.server;

import com.yahoo.collections.Tuple2;
import com.yahoo.container.jdisc.HttpResponse;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

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
    public void testEmpty() {
        Tuple2<HttpResponse, Integer> v = FeedHandler.doCheckProtocolVersion(EMPTY);
        assertTrue(v.first instanceof ErrorHttpResponse);
        assertEquals(Integer.valueOf(-1), v.second);
    }

    @Test
    public void testOneTwo() {
        Tuple2<HttpResponse, Integer> v = FeedHandler.doCheckProtocolVersion(ONE_TWO);
        assertTrue(v.first instanceof ErrorHttpResponse);
        assertEquals(Integer.valueOf(-1), v.second);
    }

    @Test
    public void testOneThree() {
        Tuple2<HttpResponse, Integer> v = FeedHandler.doCheckProtocolVersion(ONE_THREE);
        assertNull(v.first);
        assertEquals(Integer.valueOf(3), v.second);
    }

    @Test
    public void testTwoThree() {
        Tuple2<HttpResponse, Integer> v = FeedHandler.doCheckProtocolVersion(TWO_THREE);
        assertNull(v.first);
        assertEquals(Integer.valueOf(3), v.second);
    }

    @Test
    public void testOneNullThree() {
        Tuple2<HttpResponse, Integer> v = FeedHandler.doCheckProtocolVersion(ONE_NULL_THREE);
        assertNull(v.first);
        assertEquals(Integer.valueOf(3), v.second);
    }

    @Test
    public void testOneCommaThree() {
        Tuple2<HttpResponse, Integer> v = FeedHandler.doCheckProtocolVersion(ONE_COMMA_THREE);
        assertNull(v.first);
        assertEquals(Integer.valueOf(3), v.second);
    }

    @Test
    public void testOneEmptyThree() {
        Tuple2<HttpResponse, Integer> v = FeedHandler.doCheckProtocolVersion(ONE_EMPTY_THREE);
        assertNull(v.first);
        assertEquals(Integer.valueOf(3), v.second);
    }

    @Test
    public void testTooLarge() throws Exception {
        Tuple2<HttpResponse, Integer> v = FeedHandler.doCheckProtocolVersion(TOO_LARGE_NUMBER);
        assertTrue(v.first instanceof ErrorHttpResponse);
        ByteArrayOutputStream errorMsg = new ByteArrayOutputStream();
        ErrorHttpResponse errorResponse = (ErrorHttpResponse) v.first;
        errorResponse.render(errorMsg);
        assertEquals(errorMsg.toString(),
                "Could not parse X-Yahoo-Feed-Protocol-Versionheader of request (values: [1000000000]). " +
                            "Server supports protocol versions [3]");
        assertEquals(Integer.valueOf(-1), v.second);
    }

    @Test
    public void testThreeTooLarge() {
        Tuple2<HttpResponse, Integer> v = FeedHandler.doCheckProtocolVersion(THREE_TOO_LARGE_NUMBER);
        assertNull(v.first);
        assertEquals(Integer.valueOf(3), v.second);
    }

    @Test
    public void testTwoCommaTooLarge() {
        Tuple2<HttpResponse, Integer> v = FeedHandler.doCheckProtocolVersion(THREE_COMMA_TOO_LARGE_NUMBER);
        assertNull(v.first);
        assertEquals(Integer.valueOf(3), v.second);
    }

    @Test
    public void testGarbage() {
        Tuple2<HttpResponse, Integer> v = FeedHandler.doCheckProtocolVersion(GARBAGE);
        assertTrue(v.first instanceof ErrorHttpResponse);
        assertEquals(Integer.valueOf(-1), v.second);
    }

}
