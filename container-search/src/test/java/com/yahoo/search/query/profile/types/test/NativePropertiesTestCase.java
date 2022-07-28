// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.profile.types.test;

import com.yahoo.jdisc.http.HttpRequest.Method;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.search.Query;
import com.yahoo.search.query.profile.QueryProfile;
import com.yahoo.search.query.profile.types.QueryProfileType;
import com.yahoo.yolean.Exceptions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Tests that properties can not be set even if they are native, if declared not settable in the query profile
 *
 * @author bratseth
 */
public class NativePropertiesTestCase {

    @Test
    void testNativeInStrict() {
        QueryProfileType strictType = new QueryProfileType("strict");
        strictType.setStrict(true);
        QueryProfile strict = new QueryProfile("profile");
        strict.setType(strictType);

        try {
            new Query(HttpRequest.createTestRequest("?hits=10&tracelevel=5", Method.GET), strict.compile(null));
            fail("Above statement should throw");
        } catch (IllegalArgumentException e) {
            // As expected.
        }

        try {
            new Query(HttpRequest.createTestRequest("?notnative=5", Method.GET), strict.compile(null));
            fail("Above statement should throw");
        } catch (IllegalArgumentException e) {
            // As expected.
            assertTrue(Exceptions.toMessageString(e).contains(
                    "Could not set 'notnative' to '5':"
                            + " 'notnative' is not declared in query profile type 'strict', and the type is strict"));
        }
    }

}
