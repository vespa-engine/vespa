// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping;

import com.yahoo.processing.request.CompoundName;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.grouping.result.Group;
import com.yahoo.search.grouping.result.RootGroup;
import com.yahoo.search.result.Hit;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.*;

/**
 * @author Simon Thoresen Hult
 */
public class GroupingRequestTestCase {

    @Test
    public void requireThatContinuationListIsMutable() {
        GroupingRequest req = GroupingRequest.newInstance(new Query());
        assertTrue(req.continuations().isEmpty());

        Continuation foo = new Continuation() {

        };
        req.continuations().add(foo);
        assertEquals(Arrays.asList(foo), req.continuations());

        req.continuations().clear();
        assertTrue(req.continuations().isEmpty());
    }

    @Test
    public void requireThatResultIsFound() {
        Query query = new Query();
        GroupingRequest req = GroupingRequest.newInstance(query);
        Result res = new Result(query);

        res.hits().add(new Hit("foo"));
        RootGroup bar = newRootGroup(0);
        req.setResultGroup(bar);
        res.hits().add(bar);
        res.hits().add(new Hit("baz"));

        Group grp = req.getResultGroup(res);
        assertNotNull(grp);
        assertSame(bar, grp);
    }

    @Test
    public void requireThatParallelRequestsAreSupported() {
        Query query = new Query();
        Result res = new Result(query);

        GroupingRequest reqA = GroupingRequest.newInstance(query);
        RootGroup grpA = newRootGroup(0);
        reqA.setResultGroup(grpA);
        res.hits().add(grpA);

        GroupingRequest reqB = GroupingRequest.newInstance(query);
        RootGroup grpB = newRootGroup(1);
        reqB.setResultGroup(grpB);
        res.hits().add(grpB);

        Group grp = reqA.getResultGroup(res);
        assertNotNull(grp);
        assertSame(grpA, grp);

        assertNotNull(grp = reqB.getResultGroup(res));
        assertSame(grpB, grp);
    }

    @Test
    public void requireThatRemovedResultIsNull() {
        Query query = new Query();
        GroupingRequest req = GroupingRequest.newInstance(query);
        Result res = new Result(query);

        res.hits().add(new Hit("foo"));
        RootGroup bar = newRootGroup(0);
        req.setResultGroup(bar);
        res.hits().add(new Hit("baz"));

        assertNull(req.getResultGroup(res));
    }

    @Test
    public void requireThatNonGroupResultIsNull() {
        Query query = new Query();
        GroupingRequest req = GroupingRequest.newInstance(query);
        Result res = new Result(query);

        RootGroup grp = newRootGroup(0);
        req.setResultGroup(grp);
        res.hits().add(new Hit(grp.getId().toString()));

        assertNull(req.getResultGroup(res));
    }

    @Test
    public void requireThatGetRequestsReturnsAllRequests() {
        Query query = new Query();
        assertEquals(Collections.emptyList(), GroupingRequest.getRequests(query));

        GroupingRequest foo = GroupingRequest.newInstance(query);
        assertEquals(Arrays.asList(foo), GroupingRequest.getRequests(query));

        GroupingRequest bar = GroupingRequest.newInstance(query);
        assertEquals(Arrays.asList(foo, bar), GroupingRequest.getRequests(query));
    }

    @Test
    public void requireThatGetRequestThrowsIllegalArgumentOnBadProperty() throws Exception {
        Query query = new Query();
        Field propName = GroupingRequest.class.getDeclaredField("PROP_REQUEST");
        propName.setAccessible(true);
        query.properties().set((CompoundName)propName.get(null), new Object());
        try {
            GroupingRequest.getRequests(query);
            fail();
        } catch (IllegalArgumentException e) {

        }
    }

    private static RootGroup newRootGroup(int id) {
        return new RootGroup(id, new Continuation() {

        });
    }
}
