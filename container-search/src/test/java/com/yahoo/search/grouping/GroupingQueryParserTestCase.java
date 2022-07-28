// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping;

import com.yahoo.search.Query;
import com.yahoo.search.grouping.request.AllOperation;
import com.yahoo.search.grouping.request.EachOperation;
import com.yahoo.search.grouping.request.GroupingOperation;
import com.yahoo.search.searchchain.Execution;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Simon Thoresen Hult
 */
public class GroupingQueryParserTestCase {

    @Test
    void requireThatNoRequestIsSkipped() {
        assertEquals(Collections.emptyList(), executeQuery(null, null, null));
    }

    @Test
    void requireThatEmptyRequestIsSkipped() {
        assertEquals(Collections.emptyList(), executeQuery("", null, null));
    }

    @Test
    void requireThatRequestIsParsed() {
        List<GroupingRequest> lst = executeQuery("all(group(foo) each(output(max(bar))))", null, null);
        assertNotNull(lst);
        assertEquals(1, lst.size());
        GroupingRequest req = lst.get(0);
        assertNotNull(req);
        assertNotNull(req.getRootOperation());
    }

    @Test
    void requireThatRequestListIsParsed() {
        List<GroupingRequest> lst = executeQuery("all();each()", null, null);
        assertNotNull(lst);
        assertEquals(2, lst.size());
        assertTrue(lst.get(0).getRootOperation() instanceof AllOperation);
        assertTrue(lst.get(1).getRootOperation() instanceof EachOperation);
    }

    @Test
    void requireThatEachRightBelowAllParses() {
        List<GroupingRequest> lst = executeQuery("all(each(output(summary(bar))))",
                null, null);
        assertNotNull(lst);
        assertEquals(1, lst.size());
        GroupingRequest req = lst.get(0);
        assertNotNull(req);
        final GroupingOperation rootOperation = req.getRootOperation();
        assertNotNull(rootOperation);
        assertSame(AllOperation.class, rootOperation.getClass());
        assertSame(EachOperation.class, rootOperation.getChildren().get(0).getClass());
    }

    @Test
    void requireThatContinuationListIsParsed() {
        List<GroupingRequest> lst = executeQuery("all(group(foo) each(output(max(bar))))",
                "BCBCBCBEBGBCBKCBACBKCCK BCBBBBBDBF", null);
        assertNotNull(lst);
        assertEquals(1, lst.size());
        GroupingRequest req = lst.get(0);
        assertNotNull(req);
        assertNotNull(req.getRootOperation());
        assertEquals(2, req.continuations().size());
    }

    @Test
    void requireThatTimeZoneIsParsed() {
        List<GroupingRequest> lst = executeQuery("all(group(foo) each(output(max(bar))))", null, "cet");
        assertNotNull(lst);
        assertEquals(1, lst.size());
        GroupingRequest req = lst.get(0);
        assertNotNull(req);
        TimeZone time = req.getTimeZone();
        assertNotNull(time);
        assertEquals(TimeZone.getTimeZone("cet"), time);
    }

    @Test
    void requireThatTimeZoneHasUtcDefault() {
        List<GroupingRequest> lst = executeQuery("all(group(foo) each(output(max(bar))))", null, null);
        assertNotNull(lst);
        assertEquals(1, lst.size());
        GroupingRequest req = lst.get(0);
        assertNotNull(req);
        TimeZone time = req.getTimeZone();
        assertNotNull(time);
        assertEquals(TimeZone.getTimeZone("utc"), time);
    }

    private static List<GroupingRequest> executeQuery(String request, String continuation, String timeZone) {
        Query query = new Query();
        query.properties().set(GroupingQueryParser.PARAM_REQUEST, request);
        query.properties().set(GroupingQueryParser.PARAM_CONTINUE, continuation);
        query.properties().set(GroupingQueryParser.PARAM_TIMEZONE, timeZone);
        new Execution(new GroupingQueryParser(), Execution.Context.createContextStub()).search(query);
        return query.getSelect().getGrouping();
    }
}
