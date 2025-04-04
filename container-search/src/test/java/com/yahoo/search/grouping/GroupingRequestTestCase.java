// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping;

import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.grouping.result.Group;
import com.yahoo.search.grouping.result.RootGroup;
import com.yahoo.search.query.profile.QueryProfile;
import com.yahoo.search.result.Hit;
import com.yahoo.search.test.QueryTestCase;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Simon Thoresen Hult
 */
public class GroupingRequestTestCase {

    @Test
    void requireThatContinuationListIsMutable() {
        GroupingRequest req = GroupingRequest.newInstance(new Query());
        assertTrue(req.continuations().isEmpty());

        Continuation foo = new Continuation() {
            @Override
            public Continuation copy() {
                return null;
            }
        };
        req.continuations().add(foo);
        assertEquals(List.of(foo), req.continuations());

        req.continuations().clear();
        assertTrue(req.continuations().isEmpty());
    }

    @Test
    void requireThatResultIsFound() {
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
    void requireThatResultIsFoundAfterCloning() {
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
    void requireThatParallelRequestsAreSupported() {
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
    void requireThatRemovedResultIsNull() {
        Query query = new Query();
        GroupingRequest req = GroupingRequest.newInstance(query);
        Result res = new Result(query);

        res.hits().add(new Hit("foo"));
        RootGroup bar = newRootGroup(0);
        res.hits().add(new Hit("baz"));

        assertNull(req.getResultGroup(res));
    }

    @Test
    void requireThatNonGroupResultIsNull() {
        Query query = new Query();
        GroupingRequest req = GroupingRequest.newInstance(query);
        Result res = new Result(query);

        RootGroup grp = newRootGroup(0);
        res.hits().add(new Hit(grp.getId().toString()));

        assertNull(req.getResultGroup(res));
    }

    @Test
    void requireThatGetRequestsReturnsAllRequests() {
        Query query = new Query();
        assertEquals(List.of(), query.getSelect().getGrouping());

        GroupingRequest foo = GroupingRequest.newInstance(query);
        assertEquals(List.of(foo), query.getSelect().getGrouping());

        GroupingRequest bar = GroupingRequest.newInstance(query);
        assertEquals(List.of(foo, bar), query.getSelect().getGrouping());
    }

    @Test
    void requireThatGroupingPropertiesAreReflected() {
        QueryProfile profile = new QueryProfile("myProfile");
        profile.set("grouping.defaultMaxHits", "-1", null);
        profile.set("grouping.defaultMaxGroups", "-1", null);
        profile.set("grouping.defaultPrecisionFactor", "1.0", null);
        profile.set("grouping.globalMaxGroups", "-1", null);
        Query query = new Query(QueryTestCase.httpEncode("/search?queryProfile=myProfile"), profile.compile(null));

        assertEquals(List.of(), query.getSelect().getGrouping());

        GroupingRequest foo = GroupingRequest.newInstance(query);

        assertEquals(List.of(foo), query.getSelect().getGrouping());
        assertEquals(OptionalInt.of(-1), query.getSelect().getGrouping().get(0).defaultMaxHits());
        assertEquals(OptionalInt.of(-1), query.getSelect().getGrouping().get(0).defaultMaxGroups());
        assertEquals(OptionalDouble.of(1.0), query.getSelect().getGrouping().get(0).defaultPrecisionFactor());
        assertEquals(OptionalLong.of(-1), query.getSelect().getGrouping().get(0).globalMaxGroups());
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
