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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

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
            @Override
            public Continuation copy() {
                return null;
            }
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
        res.hits().add(bar);
        res.hits().add(new Hit("baz"));

        Group grp = req.getResultGroup(res);
        assertNotNull(grp);
        assertSame(bar, grp);
    }

    @Test
    public void requireThatResultIsFoundAfterCloning() {
        Query query = new Query();
        GroupingRequest req = GroupingRequest.newInstance(query);
        Result res = new Result(query.clone());

        res.hits().add(new Hit("foo"));
        RootGroup bar = newRootGroup(0);
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
        res.hits().add(grpA);

        GroupingRequest reqB = GroupingRequest.newInstance(query);
        RootGroup grpB = newRootGroup(1);
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
        res.hits().add(new Hit("baz"));

        assertNull(req.getResultGroup(res));
    }

    @Test
    public void requireThatNonGroupResultIsNull() {
        Query query = new Query();
        GroupingRequest req = GroupingRequest.newInstance(query);
        Result res = new Result(query);

        RootGroup grp = newRootGroup(0);
        res.hits().add(new Hit(grp.getId().toString()));

        assertNull(req.getResultGroup(res));
    }

    @Test
    public void requireThatGetRequestsReturnsAllRequests() {
        Query query = new Query();
        assertEquals(Collections.emptyList(), query.getSelect().getGrouping());

        GroupingRequest foo = GroupingRequest.newInstance(query);
        assertEquals(List.of(foo), query.getSelect().getGrouping());

        GroupingRequest bar = GroupingRequest.newInstance(query);
        assertEquals(List.of(foo, bar), query.getSelect().getGrouping());
    }
    

    private static RootGroup newRootGroup(int id) {
        return new RootGroup(id, new Continuation() {
            @Override
            public Continuation copy() {
                return null;
            }
        });
    }
}
