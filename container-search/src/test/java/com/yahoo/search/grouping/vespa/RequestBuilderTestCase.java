// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.vespa;

import com.yahoo.processing.IllegalInputException;
import com.yahoo.search.grouping.Continuation;
import com.yahoo.search.grouping.request.AllOperation;
import com.yahoo.search.grouping.request.AttributeValue;
import com.yahoo.search.grouping.request.EachOperation;
import com.yahoo.search.grouping.request.GroupingOperation;
import com.yahoo.search.grouping.request.SummaryValue;
import com.yahoo.searchlib.aggregation.AggregationResult;
import com.yahoo.searchlib.aggregation.CountAggregationResult;
import com.yahoo.searchlib.aggregation.ExpressionCountAggregationResult;
import com.yahoo.searchlib.aggregation.Group;
import com.yahoo.searchlib.aggregation.Grouping;
import com.yahoo.searchlib.aggregation.GroupingLevel;
import com.yahoo.searchlib.aggregation.HitsAggregationResult;
import com.yahoo.searchlib.aggregation.SumAggregationResult;
import com.yahoo.searchlib.expression.AddFunctionNode;
import com.yahoo.searchlib.expression.AndPredicateNode;
import com.yahoo.searchlib.expression.AttributeMapLookupNode;
import com.yahoo.searchlib.expression.AttributeNode;
import com.yahoo.searchlib.expression.ConstantNode;
import com.yahoo.searchlib.expression.ExpressionNode;
import com.yahoo.searchlib.expression.FilterExpressionNode;
import com.yahoo.searchlib.expression.NotPredicateNode;
import com.yahoo.searchlib.expression.OrPredicateNode;
import com.yahoo.searchlib.expression.RegexPredicateNode;
import com.yahoo.searchlib.expression.StrCatFunctionNode;
import com.yahoo.searchlib.expression.StringResultNode;
import com.yahoo.searchlib.expression.TimeStampFunctionNode;
import com.yahoo.searchlib.expression.ToStringFunctionNode;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.TimeZone;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Simon Thoresen Hult
 */
public class RequestBuilderTestCase {

    @Test
    void requireThatAllAggregationResulsAreSupported() {
        assertLayout("all(group(a) each(output(avg(b))))", "[[{ Attribute, result = [Average] }]]");
        assertLayout("all(group(a) each(output(count())))", "[[{ Attribute, result = [Count] }]]");
        assertLayout("all(group(a) each(output(max(b))))", "[[{ Attribute, result = [Max] }]]");
        assertLayout("all(group(a) each(output(min(b))))", "[[{ Attribute, result = [Min] }]]");
        assertLayout("all(group(a) each(output(sum(b))))", "[[{ Attribute, result = [Sum] }]]");
        assertLayout("all(group(a) each(each(output(summary()))))", "[[{ Attribute, result = [Hits] }]]");
        assertLayout("all(group(a) each(output(xor(b))))", "[[{ Attribute, result = [Xor] }]]");
        assertLayout("all(group(a) each(output(stddev(b))))", "[[{ Attribute, result = [StandardDeviation] }]]");
    }

    @Test
    void requireThatExpressionCountAggregationResultIsSupported() {
        RequestBuilder builder = new RequestBuilder(0);
        builder.setRootOperation(GroupingOperation.fromString("all(group(foo) output(count()))"));
        builder.build();
        AggregationResult aggr = builder.getRequestList().get(0).getRoot().getAggregationResults().get(0);
        assertTrue(aggr instanceof ExpressionCountAggregationResult);
        assertEquals(new AttributeNode("foo"), aggr.getExpression());
    }

    private List<Grouping> getRequestList(String selection) {
        RequestBuilder builder = new RequestBuilder(0);
        builder.setRootOperation(GroupingOperation.fromString(selection));
        builder.build();
        return builder.getRequestList();
    }

    @Test
    void requireThatTopNIsHonoured() {
        List<Grouping> gl = getRequestList("all(max(3) all(group(product_id) max(5) each(output(sum(price)))))");
        assertEquals(1, gl.size());
        assertEquals(3, gl.get(0).getTopN());
    }

    @Test
    void requireThatTopNIsHonouredWhenNested() {
        List<Grouping> gl = getRequestList("all( all(max(3) all(group(product_id) max(5) each(output(sum(price))))))");
        assertEquals(1, gl.size());
        assertEquals(3, gl.get(0).getTopN());
    }

    @Test
    void requireThatTopNIsInherited() {
        List<Grouping> gl = getRequestList("all(max(7) all( all(group(product_id) max(5) each(output(sum(price))))))");
        assertEquals(1, gl.size());
        assertEquals(7, gl.get(0).getTopN());
    }

    @Test
    void requireThatTopNIsMinimum() {
        List<Grouping> gl = getRequestList("all(max(7) all(max(3) all(group(product_id) max(5) each(output(sum(price))))))");
        assertEquals(1, gl.size());
        assertEquals(3, gl.get(0).getTopN());
        gl = getRequestList("all(max(3) all(max(7) all(group(product_id) max(5) each(output(sum(price))))))");
        assertEquals(1, gl.size());
        assertEquals(3, gl.get(0).getTopN());
    }

    @Test
    void requireThatTopNIsIndividual() {
        List<Grouping> gl = getRequestList("all( all(max(3) all(group(product_id) max(5) each(output(sum(price))))) all(group(filter_cluster3) order(count()) each(output(count()))))");
        assertEquals(2, gl.size());
        assertEquals(3, gl.get(0).getTopN());
        assertEquals(-1, gl.get(1).getTopN());

        gl = getRequestList("all( max(7) all(max(3) all(group(product_id) max(5) each(output(sum(price))))) all(group(filter_cluster3) order(count()) each(output(count()))))");
        assertEquals(2, gl.size());
        assertEquals(3, gl.get(0).getTopN());
        assertEquals(7, gl.get(1).getTopN());
    }

    @Test
    void requireThatAllExpressionNodesAreSupported() {
        assertLayout("all(group(add(a,b)) each(output(count())))", "[[{ Add, result = [Count] }]]");
        assertLayout("all(group(and(a,b)) each(output(count())))", "[[{ And, result = [Count] }]]");
        assertLayout("all(group(a) each(output(count())))", "[[{ Attribute, result = [Count] }]]");
        assertLayout("all(group(cat(a,b)) each(output(count())))", "[[{ Cat, result = [Count] }]]");
        assertLayout("all(group(debugwait(a, 69, true)) each(output(count())))", "[[{ DebugWait, result = [Count] }]]");
        assertLayout("all(group(docidnsspecific()) each(output(count())))", "[[{ GetDocIdNamespaceSpecific, result = [Count] }]]");
        assertLayout("all(group(1.0) each(output(count())))", "[[{ Constant, result = [Count] }]]");
        assertLayout("all(group(div(a,b)) each(output(count())))", "[[{ Divide, result = [Count] }]]");
        assertLayout("all(group(fixedwidth(a,1)) each(output(count())))", "[[{ FixedWidthBucket, result = [Count] }]]");
        assertLayout("all(group(fixedwidth(a,1.0)) each(output(count())))", "[[{ FixedWidthBucket, result = [Count] }]]");
        assertLayout("all(group(1) each(output(count())))", "[[{ Constant, result = [Count] }]]");
        assertLayout("all(group(max(a,b)) each(output(count())))", "[[{ Max, result = [Count] }]]");
        assertLayout("all(group(md5(a,1)) each(output(count())))", "[[{ MD5Bit, result = [Count] }]]");
        assertLayout("all(group(uca(a,b)) each(output(count())))", "[[{ Uca, result = [Count] }]]");
        assertLayout("all(group(uca(a,b,PRIMARY)) each(output(count())))", "[[{ Uca, result = [Count] }]]");
        assertLayout("all(group(min(a,b)) each(output(count())))", "[[{ Min, result = [Count] }]]");
        assertLayout("all(group(mod(a,b)) each(output(count())))", "[[{ Modulo, result = [Count] }]]");
        assertLayout("all(group(mul(a,b)) each(output(count())))", "[[{ Multiply, result = [Count] }]]");
        assertLayout("all(group(neg(a)) each(output(count())))", "[[{ Negate, result = [Count] }]]");
        assertLayout("all(group(normalizesubject(a)) each(output(count())))", "[[{ NormalizeSubject, result = [Count] }]]");
        assertLayout("all(group(now()) each(output(count())))", "[[{ Constant, result = [Count] }]]");
        assertLayout("all(group(or(a,b)) each(output(count())))", "[[{ Or, result = [Count] }]]");
        assertLayout("all(group(predefined(a,bucket(1,2))) each(output(count())))", "[[{ RangeBucketPreDef, result = [Count] }]]");
        assertLayout("all(group(relevance()) each(output(count())))", "[[{ Relevance, result = [Count] }]]");
        assertLayout("all(group(reverse(a)) each(output(count())))", "[[{ Reverse, result = [Count] }]]");
        assertLayout("all(group(size(a)) each(output(count())))", "[[{ NumElem, result = [Count] }]]");
        assertLayout("all(group(sort(a)) each(output(count())))", "[[{ Sort, result = [Count] }]]");
        assertLayout("all(group(strcat(a,b)) each(output(count())))", "[[{ StrCat, result = [Count] }]]");
        assertLayout("all(group('a') each(output(count())))", "[[{ Constant, result = [Count] }]]");
        assertLayout("all(group(strlen(a)) each(output(count())))", "[[{ StrLen, result = [Count] }]]");
        assertLayout("all(group(sub(a,b)) each(output(count())))", "[[{ Add, result = [Count] }]]");
        assertLayout("all(group(todouble(a)) each(output(count())))", "[[{ ToFloat, result = [Count] }]]");
        assertLayout("all(group(tolong(a)) each(output(count())))", "[[{ ToInt, result = [Count] }]]");
        assertLayout("all(group(toraw(a)) each(output(count())))", "[[{ ToRaw, result = [Count] }]]");
        assertLayout("all(group(tostring(a)) each(output(count())))", "[[{ ToString, result = [Count] }]]");
        assertLayout("all(group(time.date(a)) each(output(count())))", "[[{ StrCat, result = [Count] }]]");
        assertLayout("all(group(math.sqrt(a)) each(output(count())))", "[[{ Math, result = [Count] }]]");
        assertLayout("all(group(math.cbrt(a)) each(output(count())))", "[[{ Math, result = [Count] }]]");
        assertLayout("all(group(math.log(a)) each(output(count())))", "[[{ Math, result = [Count] }]]");
        assertLayout("all(group(math.log1p(a)) each(output(count())))", "[[{ Math, result = [Count] }]]");
        assertLayout("all(group(math.log10(a)) each(output(count())))", "[[{ Math, result = [Count] }]]");
        assertLayout("all(group(math.exp(a)) each(output(count())))", "[[{ Math, result = [Count] }]]");
        assertLayout("all(group(math.pow(a,b)) each(output(count())))", "[[{ Math, result = [Count] }]]");
        assertLayout("all(group(math.hypot(a,b)) each(output(count())))", "[[{ Math, result = [Count] }]]");
        assertLayout("all(group(math.sin(a)) each(output(count())))", "[[{ Math, result = [Count] }]]");
        assertLayout("all(group(math.asin(a)) each(output(count())))", "[[{ Math, result = [Count] }]]");
        assertLayout("all(group(math.cos(a)) each(output(count())))", "[[{ Math, result = [Count] }]]");
        assertLayout("all(group(math.acos(a)) each(output(count())))", "[[{ Math, result = [Count] }]]");
        assertLayout("all(group(math.tan(a)) each(output(count())))", "[[{ Math, result = [Count] }]]");
        assertLayout("all(group(math.atan(a)) each(output(count())))", "[[{ Math, result = [Count] }]]");
        assertLayout("all(group(math.sinh(a)) each(output(count())))", "[[{ Math, result = [Count] }]]");
        assertLayout("all(group(math.asinh(a)) each(output(count())))", "[[{ Math, result = [Count] }]]");
        assertLayout("all(group(math.cosh(a)) each(output(count())))", "[[{ Math, result = [Count] }]]");
        assertLayout("all(group(math.acosh(a)) each(output(count())))", "[[{ Math, result = [Count] }]]");
        assertLayout("all(group(math.tanh(a)) each(output(count())))", "[[{ Math, result = [Count] }]]");
        assertLayout("all(group(zcurve.x(a)) each(output(count())))", "[[{ ZCurve, result = [Count] }]]");
        assertLayout("all(group(zcurve.y(a)) each(output(count())))", "[[{ ZCurve, result = [Count] }]]");
        assertLayout("all(group(time.dayofmonth(a)) each(output(count())))", "[[{ TimeStamp, result = [Count] }]]");
        assertLayout("all(group(time.dayofweek(a)) each(output(count())))", "[[{ TimeStamp, result = [Count] }]]");
        assertLayout("all(group(time.dayofyear(a)) each(output(count())))", "[[{ TimeStamp, result = [Count] }]]");
        assertLayout("all(group(time.hourofday(a)) each(output(count())))", "[[{ TimeStamp, result = [Count] }]]");
        assertLayout("all(group(time.minuteofhour(a)) each(output(count())))", "[[{ TimeStamp, result = [Count] }]]");
        assertLayout("all(group(time.monthofyear(a)) each(output(count())))", "[[{ TimeStamp, result = [Count] }]]");
        assertLayout("all(group(time.secondofminute(a)) each(output(count())))", "[[{ TimeStamp, result = [Count] }]]");
        assertLayout("all(group(time.year(a)) each(output(count())))", "[[{ TimeStamp, result = [Count] }]]");
        assertLayout("all(group(xor(a,b)) each(output(count())))", "[[{ Xor, result = [Count] }]]");
        assertLayout("all(group(xorbit(a,1)) each(output(count())))", "[[{ XorBit, result = [Count] }]]");
    }

    @Test
    void requireThatForceSinglePassIsSupported() {
        assertForceSinglePass("all(group(foo) each(output(count())))", "[false]");
        assertForceSinglePass("all(group(foo) hint(singlepass) each(output(count())))", "[true]");
        assertForceSinglePass("all(hint(singlepass) " +
                "    all(group(foo) each(output(count())))" +
                "    all(group(bar) each(output(count()))))",
                "[true, true]");

        // it would be really nice if this test returned [true, true], but that is not how the AST is built
        assertForceSinglePass("all(all(group(foo) hint(singlepass) each(output(count())))" +
                "    all(group(bar) hint(singlepass) each(output(count()))))",
                "[false, false]");
    }

    @Test
    void requireThatThereCanBeOnlyOneBuildCall() {
        RequestBuilder builder = new RequestBuilder(0);
        builder.setRootOperation(GroupingOperation.fromString("all(group(foo) each(output(count())))"));
        builder.build();
        try {
            builder.build();
            fail();
        } catch (IllegalStateException e) {

        }
    }

    @Test
    void requireThatNullSummaryClassProvidesDefault() {
        RequestBuilder reqBuilder = new RequestBuilder(0);
        reqBuilder.setRootOperation(new AllOperation()
                .setGroupBy(new AttributeValue("foo"))
                .addChild(new EachOperation()
                        .addChild(new EachOperation()
                                .addOutput(new SummaryValue()))));
        reqBuilder.setDefaultSummaryName(null);
        reqBuilder.build();

        HitsAggregationResult hits = (HitsAggregationResult) reqBuilder.getRequestList().get(0)
                .getLevels().get(0)
                .getGroupPrototype()
                .getAggregationResults().get(0);
        assertEquals(ExpressionConverter.DEFAULT_SUMMARY_NAME, hits.getSummaryClass());
    }

    @Test
    void requireThatGroupOfGroupsAreNotSupported() {
        // "Can not group list of groups."
        assertBuildFail("all(group(a) all(group(avg(b)) each(each(each(output(summary()))))))",
                "Can not operate on list of list of groups.");
    }

    @Test
    void requireThatAnonymousListsAreNotSupported() {
        assertBuildFail("all(group(a) all(each(each(output(summary())))))",
                "Can not create anonymous list of groups.");
    }

    @Test
    void requireThatOffsetContinuationCanModifyGroupingLevel() {
        assertOffset("all(group(a) max(5) each(output(count())))",
                newOffset(2, 5),
                "[[{ tag = 2, max = [5, 11], hits = [] }]]");
        assertOffset("all(group(a) max(5) each(output(count())) as(foo)" +
                "                    each(output(count())) as(bar))",
                newOffset(2, 5),
                "[[{ tag = 2, max = [5, 11], hits = [] }]," +
                        " [{ tag = 4, max = [5, 6], hits = [] }]]");
        assertOffset("all(group(a) max(5) each(output(count())) as(foo)" +
                "                    each(output(count())) as(bar))",
                newComposite(newOffset(2, 5), newOffset(4, 10)),
                "[[{ tag = 2, max = [5, 11], hits = [] }]," +
                        " [{ tag = 4, max = [5, 16], hits = [] }]]");
    }

    @Test
    void requireThatOffsetContinuationCanModifyHitAggregator() {
        assertOffset("all(group(a) each(max(5) each(output(summary()))))",
                newOffset(3, 5),
                "[[{ tag = 2, max = [0, -1], hits = [{ tag = 3, max = [5, 11] }] }]]");
        assertOffset("all(group(a) each(max(5) each(output(summary()))) as(foo)" +
                "             each(max(5) each(output(summary()))) as(bar))",
                newOffset(3, 5),
                "[[{ tag = 2, max = [0, -1], hits = [{ tag = 3, max = [5, 11] }] }]," +
                        " [{ tag = 4, max = [0, -1], hits = [{ tag = 5, max = [5, 6] }] }]]");
        assertOffset("all(group(a) each(max(5) each(output(summary()))) as(foo)" +
                "             each(max(5) each(output(summary()))) as(bar))",
                newComposite(newOffset(3, 5), newOffset(5, 10)),
                "[[{ tag = 2, max = [0, -1], hits = [{ tag = 3, max = [5, 11] }] }]," +
                        " [{ tag = 4, max = [0, -1], hits = [{ tag = 5, max = [5, 16] }] }]]");
    }

    @Test
    void requireThatOffsetContinuationIsNotAppliedToGroupingLevelWithoutMax() {
        assertOffset("all(group(a) each(output(count())))",
                newOffset(2, 5),
                "[[{ tag = 2, max = [0, -1], hits = [] }]]");
    }

    @Test
    void requireThatOffsetContinuationIsNotAppliedToHitAggregatorWithoutMax() {
        assertOffset("all(group(a) each(each(output(summary()))))",
                newOffset(3, 5),
                "[[{ tag = 2, max = [0, -1], hits = [{ tag = 3, max = [0, -1] }] }]]");
    }

    @Test
    void requireThatUnstableContinuationsDoNotAffectRequestedGroupLists() {
        String request = "all(group(a) max(5) each(group(b) max(5) each(output(count())) as(a1_b1)" +
                "                                         each(output(count())) as(a1_b2)) as(a1)" +
                "                    each(group(b) max(5) each(output(count())) as(a2_b1)" +
                "                                         each(output(count())) as(a2_b2)) as(a2))";
        CompositeContinuation session = newComposite(newOffset(2, 5), newOffset(3, 5), newOffset(5, 5),
                newOffset(7, 5), newOffset(8, 5), newOffset(10, 5));
        assertOffset(request, newComposite(session),
                "[[{ tag = 2, max = [5, 11], hits = [] }, { tag = 3, max = [5, 11], hits = [] }]," +
                        " [{ tag = 2, max = [5, 11], hits = [] }, { tag = 5, max = [5, 11], hits = [] }]," +
                        " [{ tag = 7, max = [5, 11], hits = [] }, { tag = 10, max = [5, 11], hits = [] }]," +
                        " [{ tag = 7, max = [5, 11], hits = [] }, { tag = 8, max = [5, 11], hits = [] }]]");
        assertOffset(request, newComposite(session, newUnstableOffset(2, 10)),
                "[[{ tag = 2, max = [5, 16], hits = [] }, { tag = 3, max = [5, 11], hits = [] }]," +
                        " [{ tag = 2, max = [5, 16], hits = [] }, { tag = 5, max = [5, 11], hits = [] }]," +
                        " [{ tag = 7, max = [5, 11], hits = [] }, { tag = 10, max = [5, 11], hits = [] }]," +
                        " [{ tag = 7, max = [5, 11], hits = [] }, { tag = 8, max = [5, 11], hits = [] }]]");
        assertOffset(request, newComposite(session, newUnstableOffset(7, 10)),
                "[[{ tag = 2, max = [5, 11], hits = [] }, { tag = 3, max = [5, 11], hits = [] }]," +
                        " [{ tag = 2, max = [5, 11], hits = [] }, { tag = 5, max = [5, 11], hits = [] }]," +
                        " [{ tag = 7, max = [5, 16], hits = [] }, { tag = 10, max = [5, 11], hits = [] }]," +
                        " [{ tag = 7, max = [5, 16], hits = [] }, { tag = 8, max = [5, 11], hits = [] }]]");
        assertOffset(request, newComposite(session, newUnstableOffset(2, 10), newUnstableOffset(7, 10)),
                "[[{ tag = 2, max = [5, 16], hits = [] }, { tag = 3, max = [5, 11], hits = [] }]," +
                        " [{ tag = 2, max = [5, 16], hits = [] }, { tag = 5, max = [5, 11], hits = [] }]," +
                        " [{ tag = 7, max = [5, 16], hits = [] }, { tag = 10, max = [5, 11], hits = [] }]," +
                        " [{ tag = 7, max = [5, 16], hits = [] }, { tag = 8, max = [5, 11], hits = [] }]]");
    }

    @Test
    void requireThatUnstableContinuationsDoNotAffectRequestedHitLists() {
        String request = "all(group(a) max(5) each(max(5) each(output(summary())) as(a1_h1)" +
                "                                each(output(summary())) as(a1_h2)) as(a1)" +
                "                    each(max(5) each(output(summary())) as(a2_h1)" +
                "                                each(output(summary())) as(a2_h2)) as(a2))";
        CompositeContinuation session = newComposite(newOffset(2, 5), newOffset(3, 5), newOffset(4, 5),
                newOffset(5, 5), newOffset(6, 5), newOffset(7, 5));
        assertOffset(request, newComposite(session),
                "[[{ tag = 2, max = [5, 11], hits = [{ tag = 3, max = [5, 11] }] }]," +
                        " [{ tag = 2, max = [5, 11], hits = [{ tag = 4, max = [5, 11] }] }]," +
                        " [{ tag = 5, max = [5, 11], hits = [{ tag = 6, max = [5, 11] }] }]," +
                        " [{ tag = 5, max = [5, 11], hits = [{ tag = 7, max = [5, 11] }] }]]");
        assertOffset(request, newComposite(session, newUnstableOffset(2, 10)),
                "[[{ tag = 2, max = [5, 16], hits = [{ tag = 3, max = [5, 11] }] }]," +
                        " [{ tag = 2, max = [5, 16], hits = [{ tag = 4, max = [5, 11] }] }]," +
                        " [{ tag = 5, max = [5, 11], hits = [{ tag = 6, max = [5, 11] }] }]," +
                        " [{ tag = 5, max = [5, 11], hits = [{ tag = 7, max = [5, 11] }] }]]");
        assertOffset(request, newComposite(session, newUnstableOffset(5, 10)),
                "[[{ tag = 2, max = [5, 11], hits = [{ tag = 3, max = [5, 11] }] }]," +
                        " [{ tag = 2, max = [5, 11], hits = [{ tag = 4, max = [5, 11] }] }]," +
                        " [{ tag = 5, max = [5, 16], hits = [{ tag = 6, max = [5, 11] }] }]," +
                        " [{ tag = 5, max = [5, 16], hits = [{ tag = 7, max = [5, 11] }] }]]");
        assertOffset(request, newComposite(session, newUnstableOffset(2, 10), newUnstableOffset(5, 10)),
                "[[{ tag = 2, max = [5, 16], hits = [{ tag = 3, max = [5, 11] }] }]," +
                        " [{ tag = 2, max = [5, 16], hits = [{ tag = 4, max = [5, 11] }] }]," +
                        " [{ tag = 5, max = [5, 16], hits = [{ tag = 6, max = [5, 11] }] }]," +
                        " [{ tag = 5, max = [5, 16], hits = [{ tag = 7, max = [5, 11] }] }]]");
    }

    @Test
    void requireThatExpressionsCanBeAliased() {
        OutputWriter writer = (groupingList, transform) -> groupingList.get(0).getLevels().get(0).getGroupPrototype().getAggregationResults().get(0)
                .toString();

        RequestTest test = new RequestTest();
        test.expectedOutput = new SumAggregationResult().setTag(3).setExpression(new AttributeNode("price")).toString();
        test.request = "all(group(artist) alias(foo,sum(price)) each(output($foo)))";
        test.outputWriter = writer;
        assertOutput(test);

        test = new RequestTest();
        test.expectedOutput = new SumAggregationResult().setTag(3).setExpression(new AttributeNode("price")).toString();
        test.request = "all(group(artist) order($foo=sum(price)) each(output($foo)))";
        test.outputWriter = writer;
        assertOutput(test);
    }

    @Test
    void requireThatGroupingLayoutIsCorrect() {
        assertLayout("all(group(artist) each(max(69) output(count()) each(output(summary()))))",
                "[[{ Attribute, result = [Count, Hits] }]]");
        assertLayout("all(group(artist) each(output(count()) all(group(album) each(output(count()) all(group(song) each(max(69) output(count()) each(output(summary()))))))))",
                "[[{ Attribute, result = [Count] }, { Attribute, result = [Count] }, { Attribute, result = [Count, Hits] }]]");
        assertLayout("all(group(artist) each(output(count())))",
                "[[{ Attribute, result = [Count] }]]");
        assertLayout("all(group(artist) order(sum(price)) each(output(count())))",
                "[[{ Attribute, result = [Count, Sum], order = [[1], [AggregationRef]] }]]");
        assertLayout("all(group(artist) each(max(69) output(count()) each(output(summary(foo)))))",
                "[[{ Attribute, result = [Count, Hits] }]]");
        assertLayout("all(group(artist) each(output(count()) all(group(album) each(output(count())))))",
                "[[{ Attribute, result = [Count] }, { Attribute, result = [Count] }]]");
        assertLayout("all(group(artist) max(5) each(output(count()) all(group(album) max(3) each(output(count())))))",
                "[[{ Attribute, max = [6, 6], result = [Count] }, { Attribute, max = [4, 4], result = [Count] }]]");
        assertLayout("all(group(artist) max(5) each(output(count()) all(group(album) max(3) each(output(count())))))",
                "[[{ Attribute, max = [6, 6], result = [Count] }, { Attribute, max = [4, 4], result = [Count] }]]");
        assertLayout("all(group(foo) max(10) each(output(count()) all(group(bar) max(10) each(output(count())))))",
                "[[{ Attribute, max = [11, 11], result = [Count] }, { Attribute, max = [11, 11], result = [Count] }]]");
        assertLayout("all(group(a) max(5) each(max(69) output(count()) each(output(summary()))))",
                "[[{ Attribute, max = [6, 6], result = [Count, Hits] }]]");
        assertLayout("all(group(a) max(5) each(output(count()) all(group(b) max(5) each(max(69) output(count()) each(output(summary()))))))",
                "[[{ Attribute, max = [6, 6], result = [Count] }, { Attribute, max = [6, 6], result = [Count, Hits] }]]");
        assertLayout("all(group(a) max(5) each(output(count()) all(group(b) max(5) each(output(count()) all(group(c) max(5) each(max(69) output(count()) each(output(summary()))))))))",
                "[[{ Attribute, max = [6, 6], result = [Count] }, { Attribute, max = [6, 6], result = [Count] }, { Attribute, max = [6, 6], result = [Count, Hits] }]]");
        assertLayout("all(group(fixedwidth(n,3)) max(5) each(output(count()) all(group(a) max(2) each(output(count())))))",
                "[[{ FixedWidthBucket, max = [6, 6], result = [Count] }, { Attribute, max = [3, 3], result = [Count] }]]");
        assertLayout("all(group(fixedwidth(n,3)) max(5) each(output(count()) all(group(a) max(2) each(output(count())))))",
                "[[{ FixedWidthBucket, max = [6, 6], result = [Count] }, { Attribute, max = [3, 3], result = [Count] }]]");
        assertLayout("all(group(fixedwidth(n,3)) max(5) each(output(count()) all(group(a) max(2) each(max(1) output(count()) each(output(summary()))))))",
                "[[{ FixedWidthBucket, max = [6, 6], result = [Count] }, { Attribute, max = [3, 3], result = [Count, Hits] }]]");
        assertLayout("all(group(predefined(n,bucket(1,3),bucket(6,9))) each(output(count())))",
                "[[{ RangeBucketPreDef, result = [Count] }]]");
        assertLayout("all(group(predefined(f,bucket(1.0,3.0),bucket(6.0,9.0))) each(output(count())))",
                "[[{ RangeBucketPreDef, result = [Count] }]]");
        assertLayout("all(group(predefined(s,bucket(\"ab\",\"cd\"),bucket(\"ef\",\"gh\"))) each(output(count())))",
                "[[{ RangeBucketPreDef, result = [Count] }]]");
        assertLayout("all(group(a) max(5) each(output(count())))",
                "[[{ Attribute, max = [6, 6], result = [Count] }]]");
        assertLayout("all(group(a) max(5) each(output(count())))",
                "[[{ Attribute, max = [6, 6], result = [Count] }]]");
        assertLayout("all(max(9) all(group(a) each(output(count()))))",
                "[[{ Attribute, result = [Count] }]]");
        assertLayout("all(where(true) all(group(a) each(output(count()))))",
                "[[{ Attribute, result = [Count] }]]");
        assertLayout("all(group(a) order(sum(n)) each(output(count())))",
                "[[{ Attribute, result = [Count, Sum], order = [[1], [AggregationRef]] }]]");
        assertLayout("all(group(a) max(2) each(output(count())))",
                "[[{ Attribute, max = [3, 3], result = [Count] }]]");
        assertLayout("all(group(a) max(2) precision(10) each(output(count())))",
                "[[{ Attribute, max = [3, 10], result = [Count] }]]");
        assertLayout("all(group(fixedwidth(a,1)) each(output(count())))",
                "[[{ FixedWidthBucket, result = [Count] }]]");
    }

    @Test
    void requireThatAggregatorCanBeUsedAsArgumentToOrderByFunction() {
        assertLayout("all(group(a) order(sum(price) * count()) each(output(count())))",
                "[[{ Attribute, result = [Count, Sum], order = [[1], [Multiply]] }]]");
        assertLayout("all(group(a) order(sum(price) + 4) each(output(sum(price))))",
                "[[{ Attribute, result = [Sum], order = [[1], [Add]] }]]");
        assertLayout("all(group(a) order(sum(price) + 4, count()) each(output(sum(price))))",
                "[[{ Attribute, result = [Sum, Count], order = [[1, 2], [Add, AggregationRef]] }]]");
        assertLayout("all(group(a) order(sum(price) + 4, -count()) each(output(sum(price))))",
                "[[{ Attribute, result = [Sum, Count], order = [[1, -2], [Add, AggregationRef]] }]]");
    }

    @Test
    void requireThatSameAggregatorCanBeUsedMultipleTimes() {
        assertLayout("all(group(a) each(output(count() as(b),count() as(c))))",
                "[[{ Attribute, result = [Count, Count] }]]");
    }

    @Test
    void requireThatSiblingAggregatorsCanNotShareSameLabel() {
        assertBuildFail("all(group(a) each(output(count(),count())))",
                "Can not use output label 'count()' for multiple siblings.");
        assertBuildFail("all(group(a) each(output(count() as(b),count() as(b))))",
                "Can not use output label 'b' for multiple siblings.");
    }

    @Test
    void requireThatOrderByReusesOutputResults() {
        assertLayout("all(group(a) order(count()) each(output(count())))",
                "[[{ Attribute, result = [Count], order = [[1], [AggregationRef]] }]]");
        assertLayout("all(group(a) order(count()) each(output(count() as(b))))",
                "[[{ Attribute, result = [Count], order = [[1], [AggregationRef]] }]]");
    }

    @Test
    void requireThatNoopBranchesArePruned() {
        assertLayout("all()", "[]");
        assertLayout("all(group(a))", "[]");
        assertLayout("all(group(a) each())", "[]");

        String expectedA = "[{ Attribute, result = [Count] }]";
        assertLayout("all(group(a) each(output(count())))",
                List.of(expectedA).toString());
        assertLayout("all(group(a) each(output(count()) all()))",
                List.of(expectedA).toString());
        assertLayout("all(group(a) each(output(count()) all(group(b))))",
                List.of(expectedA).toString());
        assertLayout("all(group(a) each(output(count()) all(group(b) each())))",
                List.of(expectedA).toString());
        assertLayout("all(group(a) each(output(count()) all(group(b) each())))",
                List.of(expectedA).toString());
        assertLayout("all(group(a) each(output(count()) all(group(b) each())) as(foo)" +
                "             each())",
                List.of(expectedA).toString());
        assertLayout("all(group(a) each(output(count()) all(group(b) each())) as(foo)" +
                "             each(group(b)))",
                List.of(expectedA).toString());
        assertLayout("all(group(a) each(output(count()) all(group(b) each())) as(foo)" +
                "             each(group(b) each()))",
                List.of(expectedA).toString());

        String expectedB = "[{ Attribute }, { Attribute, result = [Count] }]";
        assertLayout("all(group(a) each(output(count()) all(group(b) each())) as(foo)" +
                "             each(group(b) each(output(count()))))",
                List.of(expectedB, expectedA).toString());
    }

    @Test
    void requireThatAggregationLevelIsValidatedFails() {
        assertBuildFail("all(group(artist) output(sum(length)))",
                "Expression 'length' not applicable for single group.");
        assertBuild("all(group(artist) each(output(count())))");
        assertBuildFail("all(group(artist) each(group(album) output(sum(length))))",
                "Expression 'length' not applicable for single group.");
        assertBuild("all(group(artist) each(group(album) each(output(count()))))");
    }

    @Test
    void requireThatCountOnListOfGroupsIsValidated() {
        assertBuild("all(group(artist) output(count()))");
        assertBuild("all(group(artist) each(group(album) output(count())))");
    }

    @Test
    void requireThatGroupByIsValidated() {
        assertBuild("all(group(artist) each(output(count())))");
        assertBuildFail("all(group(sum(artist)) each(output(count())))",
                "Expression 'sum(artist)' not applicable for single hit.");
        assertBuild("all(group(artist) each(group(album) each(output(count()))))");
        assertBuildFail("all(group(artist) each(group(sum(album)) each(output(count()))))",
                "Expression 'sum(album)' not applicable for single hit.");
    }

    @Test
    void requireThatGroupingLevelIsValidated() {
        assertBuild("all(group(artist))");
        assertBuild("all(group(artist) each(group(album)))");
        assertBuildFail("all(group(artist) all(group(sum(price))))",
                "Can not operate on list of list of groups.");
        assertBuild("all(group(artist) each(group(album) each(group(song))))");
        assertBuildFail("all(group(artist) each(group(album) all(group(sum(price)))))",
                "Can not operate on list of list of groups.");
    }

    @Test
    void requireThatOrderByIsValidated() {
        assertBuildFail("all(order(length))",
                "Can not order single group content.");
        assertBuild("all(group(artist) order(sum(length)))");
        assertBuildFail("all(group(artist) each(order(length)))",
                "Can not order single group content.");
        assertBuild("all(group(artist) each(group(album) order(sum(length))))");
        assertBuildFail("all(group(artist) each(group(album) each(order(length))))",
                "Can not order single group content.");
    }

    @Test
    void requireThatOrderByHasCorrectReference() {
        assertOrderBy("all(group(a) order(count()) each(output(count())))", "[[[1]]]");
        assertOrderBy("all(group(a) order(-count()) each(output(count())))", "[[[-1]]]");
        assertOrderBy("all(group(a) order(count()) each(output(count(),sum(b))))", "[[[1]]]");
        assertOrderBy("all(group(a) order(-count()) each(output(count(),sum(b))))", "[[[-1]]]");
        assertOrderBy("all(group(a) order(count()) each(output(sum(b), count())))", "[[[1]]]");
        assertOrderBy("all(group(a) order(-count()) each(output(sum(b), count())))", "[[[-1]]]");

        assertOrderBy("all(group(a) order(count(),sum(b)) each(output(count(),sum(b))))", "[[[1, 2]]]");
        assertOrderBy("all(group(a) order(count(),-sum(b)) each(output(count(),sum(b))))", "[[[1, -2]]]");
        assertOrderBy("all(group(a) order(-count(),sum(b)) each(output(count(),sum(b))))", "[[[-1, 2]]]");
        assertOrderBy("all(group(a) order(-count(),-sum(b)) each(output(count(),sum(b))))", "[[[-1, -2]]]");

        // because order() is resolved before output(), index follows order() statement
        assertOrderBy("all(group(a) order(count(),sum(b)) each(output(sum(b), count())))", "[[[1, 2]]]");
        assertOrderBy("all(group(a) order(count(),-sum(b)) each(output(sum(b), count())))", "[[[1, -2]]]");
        assertOrderBy("all(group(a) order(-count(),sum(b)) each(output(sum(b), count())))", "[[[-1, 2]]]");
        assertOrderBy("all(group(a) order(-count(),-sum(b)) each(output(sum(b), count())))", "[[[-1, -2]]]");

        assertOrderBy("all(group(a) order(count()) each(output(count())) as(foo)" +
                "                            each(output(sum(b))) as(bar))",
                "[[[1]], [[1]]]");
    }


    @Test
    void requireThatWhereIsValidated() {
        assertBuild("all(where(true))");
        assertBuild("all(where($query))");
        assertBuildFail("all(where(foo))",
                "Operation 'where' does not support 'foo'.");
        assertBuildFail("all(group(artist) where(true))",
                "Can not apply 'where' to non-root group.");
    }

    @Test
    void requireThatRootAggregationCanBeTransformed() {
        RequestTest test = new RequestTest();
        test.expectedOutput = CountAggregationResult.class.getName();
        test.request = "all(output(count()))";
        test.outputWriter = (groupingList, transform) -> groupingList.get(0).getRoot().getAggregationResults().get(0).getClass().getName();
        assertOutput(test);
    }

    @Test
    void requireThatExpressionsCanBeLabeled() {
        assertLabel("all(group(a) each(output(count())))",
                "[[{ label = 'a', results = [count()] }]]");
        assertLabel("all(group(a) each(output(count())) as(b))",
                "[[{ label = 'b', results = [count()] }]]");
        assertLabel("all(group(a) each(group(b) each(output(count()))))",
                "[[{ label = 'a', results = [] }, { label = 'b', results = [count()] }]]");
        assertLabel("all(group(a) each(group(b) each(group(c) each(output(count())))))",
                "[[{ label = 'a', results = [] }, { label = 'b', results = [] }, { label = 'c', results = [count()] }]]");
        assertBuildFail("all(group(a) each(output(count())) each(output(count())))",
                "Can not use group list label 'a' for multiple siblings.");
        assertBuildFail("all(all(group(a) each(output(count())))" +
                "    all(group(a) each(output(count()))))",
                "Can not use group list label 'a' for multiple siblings.");
        assertLabel("all(group(a) each(output(count())) as(a1)" +
                "             each(output(count())) as(a2))",
                "[[{ label = 'a1', results = [count()] }], [{ label = 'a2', results = [count()] }]]");
        assertLabel("all(group(a) each(all(group(b) each(output(count())))" +
                "                  all(group(c) each(output(count())))))",
                "[[{ label = 'a', results = [] }, { label = 'b', results = [count()] }], [{ label = 'a', results = [] }, { label = 'c', results = [count()] }]]");
        assertLabel("all(group(a) each(group(b) each(output(count()))) as(a1)" +
                "             each(group(b) each(output(count()))) as(a2))",
                "[[{ label = 'a1', results = [] }, { label = 'b', results = [count()] }], [{ label = 'a2', results = [] }, { label = 'b', results = [count()] }]]");
        assertLabel("all(group(a) each(group(b) each(group(c) each(output(count())))) as(a1)" +
                "             each(group(b) each(group(e) each(output(count())))) as(a2))",
                "[[{ label = 'a1', results = [] }, { label = 'b', results = [] }, { label = 'c', results = [count()] }]," +
                        " [{ label = 'a2', results = [] }, { label = 'b', results = [] }, { label = 'e', results = [count()] }]]");
        assertLabel("all(group(a) each(group(b) each(output(count())) as(b1)" +
                "                           each(output(count())) as(b2)))",
                "[[{ label = 'a', results = [] }, { label = 'b1', results = [count()] }]," +
                        " [{ label = 'a', results = [] }, { label = 'b2', results = [count()] }]]");

        assertBuildFail("all(group(a) each(each(output(summary() as(foo)))))",
                "Can not label expression 'summary()'.");
        assertLabel("all(group(foo) each(each(output(summary()))))",
                "[[{ label = 'foo', results = [hits] }]]");
        assertLabel("all(group(foo) each(each(output(summary())) as(bar)))",
                "[[{ label = 'foo', results = [bar] }]]");
        assertLabel("all(group(foo) each(each(output(summary())) as(bar)) as(baz))",
                "[[{ label = 'baz', results = [bar] }]]");
        assertLabel("all(group(foo) each(each(output(summary())) as(bar)" +
                "                    each(output(summary())) as(baz)))",
                "[[{ label = 'foo', results = [bar] }]," +
                        " [{ label = 'foo', results = [baz] }]]");
        assertLabel("all(group(foo) each(each(output(summary())))" +
                "               each(each(output(summary()))) as(bar))",
                "[[{ label = 'bar', results = [hits] }]," +
                        " [{ label = 'foo', results = [hits] }]]");
    }

    @Test
    void requireThatOrderByResultsAreNotLabeled() {
        assertLabel("all(group(a) each(output(min(b), max(b), avg(b))))",
                "[[{ label = 'a', results = [min(b), max(b), avg(b)] }]]");
        assertLabel("all(group(a) order(min(b)) each(output(max(b), avg(b))))",
                "[[{ label = 'a', results = [max(b), avg(b), null] }]]");
        assertLabel("all(group(a) order(min(b), max(b)) each(output(avg(b))))",
                "[[{ label = 'a', results = [avg(b), null, null] }]]");
    }

    @Test
    void requireThatTimeZoneIsAppliedToTimeFunctions() {
        for (String timePart : List.of("dayofmonth", "dayofweek", "dayofyear", "hourofday",
                                       "minuteofhour", "monthofyear", "secondofminute", "year"))
        {
            String request = "all(output(avg(time." + timePart + "(foo))))";
            assertTimeZone(request, "GMT-2", -7200L);
            assertTimeZone(request, "GMT-1", -3600L);
            assertTimeZone(request, "GMT", null);
            assertTimeZone(request, "GMT+1", 3600L);
            assertTimeZone(request, "GMT+2", 7200L);
        }
    }

    @Test
    void requireThatTimeDateIsExpanded() {
        RequestTest test = new RequestTest();
        test.expectedOutput = new StrCatFunctionNode()
                .addArg(new ToStringFunctionNode(new TimeStampFunctionNode(new AttributeNode("foo"),
                        TimeStampFunctionNode.TimePart.Year, true)))
                .addArg(new ConstantNode(new StringResultNode("-")))
                .addArg(new ToStringFunctionNode(new TimeStampFunctionNode(new AttributeNode("foo"),
                        TimeStampFunctionNode.TimePart.Month, true)))
                .addArg(new ConstantNode(new StringResultNode("-")))
                .addArg(new ToStringFunctionNode(new TimeStampFunctionNode(new AttributeNode("foo"),
                        TimeStampFunctionNode.TimePart.MonthDay, true)))
                .toString();
        test.request = "all(output(avg(time.date(foo))))";
        test.outputWriter = (groupingList, transform) -> groupingList.get(0).getRoot().getAggregationResults().get(0).getExpression().toString();
        assertOutput(test);
    }

    @Test
    void requireThatNowIsResolvedToCurrentTime() {
        RequestTest test = new RequestTest();
        test.expectedOutput = Boolean.toString(true);
        test.request = "all(output(avg(now() - foo)))";
        test.outputWriter = new OutputWriter() {
            final long before = System.currentTimeMillis();

            @Override
            public String write(List<Grouping> groupingList, GroupingTransform transform) {
                AddFunctionNode add =
                        (AddFunctionNode) groupingList.get(0).getRoot().getAggregationResults().get(0).getExpression();
                long nowValue = ((ConstantNode) add.getArg(0)).getValue().getInteger();
                boolean preCond = nowValue >= (before / 1000);
                long after = System.currentTimeMillis();
                boolean postCond = nowValue <= (after / 1000);
                boolean allOk = preCond && postCond;
                return Boolean.toString(allOk);
            }
        };
        assertOutput(test);
    }

    @Test
    void requireThatAttributeMapLookupNodeIsCreatedFromKey() {
        RequestTest test = new RequestTest();
        test.expectedOutput = AttributeMapLookupNode.fromKey("map{\"my_key\"}", "map.key", "map.value", "my_key").toString();
        test.request = "all(group(map{\"my_key\"}) each(output(count())))";
        test.outputWriter = (groupingList, transform) -> groupingList.get(0).getLevels().get(0).getExpression().toString();
        assertOutput(test);
    }

    @Test
    void requireThatAttributeMapLookupNodeIsCreatedFromKeySourceAttribute() {
        RequestTest test = new RequestTest();
        test.expectedOutput = AttributeMapLookupNode.fromKeySourceAttribute("map{attribute(key_source)}", "map.key", "map.value", "key_source").toString();
        test.request = "all(group(map{attribute(key_source)}) each(output(count())))";
        test.outputWriter = (groupingList, transform) -> groupingList.get(0).getLevels().get(0).getExpression().toString();
        assertOutput(test);
    }

    @Test
    void require_that_default_max_values_from_request_builder_restricts_max_groups_and_hits() {
        int defaultMaxHits = 19;
        int defaultMaxGroups = 7;
        RequestBuilder builder = new RequestBuilder(0)
                .setDefaultMaxGroups(defaultMaxGroups)
                .setDefaultMaxHits(defaultMaxHits)
                .setRootOperation(GroupingOperation.fromString("all(group(foo)each(each(output(summary()))))"));
        builder.build();
        List<Grouping> requests = builder.getRequestList();
        assertEquals(defaultMaxGroups + 1, requests.get(0).getLevels().get(0).getMaxGroups());
        HitsAggregationResult hitsAggregation =
                (HitsAggregationResult) requests.get(0).getLevels().get(0).getGroupPrototype().getAggregationResults().get(0);
        assertEquals(defaultMaxHits + 1, hitsAggregation.getMaxHits());
    }

    @Test
    void require_that_default_max_values_from_request_builder_respects_explicit_max() {
        {
            RequestBuilder builder = new RequestBuilder(0)
                    .setDefaultMaxGroups(7)
                    .setDefaultMaxHits(19)
                    .setRootOperation(GroupingOperation.fromString("all(group(foo)max(11)each(max(21)each(output(summary()))))"));
            builder.build();
            List<Grouping> requests = builder.getRequestList();
            assertEquals(12, requests.get(0).getLevels().get(0).getMaxGroups());
            HitsAggregationResult hitsAggregation =
                    (HitsAggregationResult) requests.get(0).getLevels().get(0).getGroupPrototype().getAggregationResults().get(0);
            assertEquals(22, hitsAggregation.getMaxHits());
        }
        {
            RequestBuilder builder = new RequestBuilder(0)
                    .setDefaultMaxGroups(7)
                    .setDefaultMaxHits(19)
                    .setRootOperation(GroupingOperation.fromString("all(group(foo)max(inf)each(max(inf)each(output(summary()))))"));
            builder.build();
            List<Grouping> requests = builder.getRequestList();
            assertEquals(-1, requests.get(0).getLevels().get(0).getMaxGroups());
            HitsAggregationResult hitsAggregation =
                    (HitsAggregationResult) requests.get(0).getLevels().get(0).getGroupPrototype().getAggregationResults().get(0);
            assertEquals(-1, hitsAggregation.getMaxHits());
        }
    }

    @Test
    void require_that_total_groups_and_summaries_calculation_is_correct() {
        assertTotalGroupsAndSummaries(5, "all(group(a) max(5) each(output(count())))");
        assertTotalGroupsAndSummaries(5 + 5 * 7, "all(group(a) max(5) each(max(7) each(output(summary()))))");
        assertTotalGroupsAndSummaries(3 + 3 * 5 + 3 * 5 * 7 + 3 * 5 * 7 * 11,
                "all( group(a) max(3) each(output(count())" +
                        "     all(group(b) max(5) each(output(count())" +
                        "         all(group(c) max(7) each(max(11) output(count())" +
                        "             each(output(summary()))))))))");
        assertTotalGroupsAndSummaries(2 * (3 + 3 * 5),
                "all(" +
                        "   all(group(a) max(3) each(output(count()) max(5) each(output(summary())))) " +
                        "   all(group(b) max(3) each(output(count()) max(5) each(output(summary())))))");
        assertTotalGroupsAndSummaries(3, "all(group(predefined(a,bucket(1,3),bucket(6,9))) each(output(count())))");
    }

    @Test
    void require_that_total_groups_restriction_can_be_disabled() {
        assertTotalGroupsAndSummaries(3 + 3 * 5 + 3 * 5 * 7 + 3 * 5 * 7 * 100,
                -1, // disable
                "all( group(a) max(3) each(output(count())" +
                        "     all(group(b) max(5) each(output(count())" +
                        "         all(group(c) max(7) each(max(100) output(count())" +
                        "             each(output(summary()))))))))");
    }

    @Test
    void require_that_unbounded_queries_fails_when_global_max_is_enabled() {
        assertQueryFailsOnGlobalMax(4, "all(group(a) max(5) each(output(count())))", "5 > 4");
        assertQueryFailsOnGlobalMax(Long.MAX_VALUE, "all(group(a) each(output(count())))", "unbounded number of groups");
        assertQueryFailsOnGlobalMax(Long.MAX_VALUE, "all(group(a) max(5) each(each(output(summary()))))", "unbounded number of summaries");
    }

    @Test
    void require_that_default_precision_factor_overrides_implicit_precision() {
        int factor = 3;
        RequestBuilder builder = new RequestBuilder(0)
                .setDefaultPrecisionFactor(factor)
                .setRootOperation(GroupingOperation.fromString("all(group(foo)max(5)each(output(count())))"));
        builder.build();
        assertEquals(5 * factor, builder.getRequestList().get(0).getLevels().get(0).getPrecision());
    }

    @Test
    void require_that_explicit_precision_has_precedence() {
        RequestBuilder builder = new RequestBuilder(0)
                .setDefaultPrecisionFactor(3)
                .setRootOperation(GroupingOperation.fromString("all(group(foo)max(5)precision(10)each(output(count())))"));
        builder.build();
        assertEquals(10, builder.getRequestList().get(0).getLevels().get(0).getPrecision());
    }

    @Test
    void require_that_filter_layout_is_correct() {
        assertLayout("all(group(a) filter(regex(\".*suffix$\", a)) each(output(count())))",
                "[[{ Attribute, filter = [Regex [Attribute]], result = [Count] }]]");
        assertLayout("all(group(time.dayofmonth(a)) filter(regex(\".*suffix$\", b)) each(output(count())))",
                "[[{ TimeStamp, filter = [Regex [Attribute]], result = [Count] }]]");
        assertLayout("all(group(time.dayofmonth(a)) filter(regex(\"^\\d\\d$\", tostring(array.at(mylongarray, 0)))) each(output(count())))",
                "[[{ TimeStamp, filter = [Regex [ToString]], result = [Count] }]]");
        assertLayout("all(group(a) each(group(b) filter(regex(\".*suffix$\", c)) each(output(count()))))",
                "[[{ Attribute }, { Attribute, filter = [Regex [Attribute]], result = [Count] }]]");
        assertLayout("all(group(a) filter(regex(\".*suffix$\", b)) each(group(c) each(output(count()))))",
                "[[{ Attribute, filter = [Regex [Attribute]] }, { Attribute, result = [Count] }]]");
    }

    @Test
    void require_that_filter_predicate_layout_is_correct() {
        assertLayout("all(group(a) filter(not(regex(\".*suffix$\", a))) each(output(count())))",
                "[[{ Attribute, filter = [Not [Regex [Attribute]]], result = [Count] }]]");
        assertLayout("all(group(a) filter(or(regex(\".*suffix$\", a),regex(\".*suffix$\", b))) each(output(count())))",
                "[[{ Attribute, filter = [Or [Regex [Attribute], Regex [Attribute]]], result = [Count] }]]");
        assertLayout("all(group(a) filter(and(regex(\".*suffix$\", a),regex(\".*suffix$\", b))) each(output(count())))",
                "[[{ Attribute, filter = [And [Regex [Attribute], Regex [Attribute]]], result = [Count] }]]");
        assertLayout("all(group(a) filter(and(or(regex(\".*suffix$\", a),regex(\".*suffix$\", b)), not(regex(\".*suffix$\", c)))) each(output(count())))",
                "[[{ Attribute, filter = [And [Or [Regex [Attribute], Regex [Attribute]], Not [Regex [Attribute]]]], result = [Count] }]]");
    }

    private static void assertTotalGroupsAndSummaries(long expected, String query) {
        assertTotalGroupsAndSummaries(expected, Long.MAX_VALUE, query);
    }

    private static void assertTotalGroupsAndSummaries(long expected, long globalMaxGroups, String query) {
        RequestBuilder builder = new RequestBuilder(0)
                .setRootOperation(GroupingOperation.fromString(query)).setGlobalMaxGroups(globalMaxGroups);
        builder.build();
        if (globalMaxGroups >= 0) // otherwise, totalGroupsAndSummaries is not computed
            assertEquals(expected, builder.totalGroupsAndSummaries().orElseThrow());
    }

    private static void assertQueryFailsOnGlobalMax(long globalMax, String query, String errorSubstring) {
        RequestBuilder builder = new RequestBuilder(0)
                .setRootOperation(GroupingOperation.fromString(query)).setGlobalMaxGroups(globalMax);
        try {
            builder.build();
            fail();
        } catch (IllegalInputException e) {
            assertTrue(e.getMessage().contains(errorSubstring));
        }
    }

    private static CompositeContinuation newComposite(EncodableContinuation... conts) {
        CompositeContinuation ret = new CompositeContinuation();
        for (EncodableContinuation cont : conts) {
            ret.add(cont);
        }
        return ret;
    }

    private static OffsetContinuation newOffset(int tag, int offset) {
        return new OffsetContinuation(ResultId.valueOf(0), tag, offset, 0);
    }

    private static OffsetContinuation newUnstableOffset(int tag, int offset) {
        return new OffsetContinuation(ResultId.valueOf(0), tag, offset, OffsetContinuation.FLAG_UNSTABLE);
    }

    private static void assertBuild(String request) {
        RequestTest test = new RequestTest();
        test.request = request;
        assertOutput(test);
    }

    private static void assertBuildFail(String request, String expectedException) {
        RequestTest test = new RequestTest();
        test.request = request;
        test.expectedException = expectedException;
        assertOutput(test);
    }

    private static void assertTimeZone(String request, String timeZone, Long expectedOutput) {
        RequestTest test = new RequestTest();
        test.request = request;
        test.timeZone = timeZone;
        test.outputWriter = (groupingList, transform) -> {
            Long timeOffset = null;
            ExpressionNode node =
                    ((TimeStampFunctionNode)groupingList.get(0).getRoot().getAggregationResults().get(0)
                                                        .getExpression()).getArg(0);
            if (node instanceof AddFunctionNode) {
                timeOffset = (((ConstantNode)((AddFunctionNode)node).getArg(1)).getValue()).getInteger();
            }
            return String.valueOf(timeOffset);
        };
        test.expectedOutput = String.valueOf(expectedOutput);
        assertOutput(test);
    }

    private static void assertLabel(String request, String expectedOutput) {
        assertOutput(request, new LabelWriter(), expectedOutput);
    }

    private static void assertLayout(String request, String expectedOutput) {
        assertOutput(request, new LayoutWriter(), expectedOutput);
    }

    private static void assertOrderBy(String request, String expectedOutput) {
        assertOutput(request, new OrderByWriter(), expectedOutput);
    }

    private static void assertOffset(String request, Continuation continuation, String expectedOutput) {
        RequestTest ret = new RequestTest();
        ret.request = request;
        ret.continuation = continuation;
        ret.outputWriter = new OffsetWriter();
        ret.expectedOutput = expectedOutput;
        assertOutput(ret);
    }

    private static void assertForceSinglePass(String request, String expectedOutput) {
        assertOutput(request, new ForceSinglePassWriter(), expectedOutput);
    }

    private static void assertOutput(String request, OutputWriter writer, String expectedOutput) {
        RequestTest ret = new RequestTest();
        ret.request = request;
        ret.outputWriter = writer;
        ret.expectedOutput = expectedOutput;
        assertOutput(ret);
    }

    private static void assertOutput(RequestTest test) {
        RequestBuilder builder = new RequestBuilder(0);
        builder.setRootOperation(GroupingOperation.fromString(test.request));
        builder.setTimeZone(TimeZone.getTimeZone(test.timeZone));
        builder.addContinuations(test.continuation != null ? List.of(test.continuation) : List.of());
        try {
            builder.build();
            if (test.expectedException != null) {
                fail("Expected exception '" + test.expectedException + "'.");
            }
        } catch (RuntimeException e) {
            if (test.expectedException == null) {
                throw e;
            }
            assertEquals(test.expectedException, e.getMessage());
            return;
        }
        if (test.outputWriter != null) {
            String output = test.outputWriter.write(builder.getRequestList(), builder.getTransform());
            assertEquals(test.expectedOutput, output);
        }
    }

    private static class RequestTest {

        String request;
        String timeZone = "utc";
        String expectedException;
        String expectedOutput;
        OutputWriter outputWriter;
        Continuation continuation;

    }

    private interface OutputWriter {

        String write(List<Grouping> groupingList, GroupingTransform transform);

    }

    private static class OffsetWriter implements OutputWriter {

        @Override
        public String write(List<Grouping> groupingList, GroupingTransform transform) {
            List<String> foo = new LinkedList<>();
            for (Grouping grouping : groupingList) {
                List<String> bar = new LinkedList<>();
                for (GroupingLevel level : grouping.getLevels()) {
                    List<String> baz = new LinkedList<>();
                    for (AggregationResult result : level.getGroupPrototype().getAggregationResults()) {
                        if (result instanceof HitsAggregationResult) {
                            int tag = result.getTag();
                            baz.add("{ tag = " + tag + ", max = [" + transform.getMax(tag) + ", " +
                                    ((HitsAggregationResult)result).getMaxHits() + "] }");
                        }
                    }
                    int tag = level.getGroupPrototype().getTag();
                    bar.add("{ tag = " + tag + ", max = [" + transform.getMax(tag) + ", " + level.getMaxGroups() +
                            "], hits = " + baz.toString() + " }");
                }
                foo.add(bar.toString());
            }
            Collections.sort(foo);
            return foo.toString();
        }
    }

    private static class LabelWriter implements OutputWriter {

        @Override
        public String write(List<Grouping> groupingList, GroupingTransform transform) {
            List<String> foo = new LinkedList<>();
            for (Grouping grouping : groupingList) {
                List<String> bar = new LinkedList<>();
                for (GroupingLevel level : grouping.getLevels()) {
                    List<String> baz = new LinkedList<>();
                    for (AggregationResult result : level.getGroupPrototype().getAggregationResults()) {
                        baz.add(transform.getLabel(result.getTag()));
                    }
                    bar.add("{ label = '" + transform.getLabel(level.getGroupPrototype().getTag()) +
                            "', results = " + baz.toString() + " }");
                }
                foo.add(bar.toString());
            }
            Collections.sort(foo);
            return foo.toString();
        }
    }

    private static class LayoutWriter implements OutputWriter {

        @Override
        public String write(List<Grouping> groupingList, GroupingTransform transform) {
            List<String> foo = new LinkedList<>();
            for (Grouping grouping : groupingList) {
                List<String> bar = new LinkedList<>();
                for (GroupingLevel level : grouping.getLevels()) {
                    StringBuilder str = new StringBuilder("{ ");
                    str.append(toSimpleName(level.getExpression())).append(", ");
                    if (level.getFilter() != null) {
                        str.append("filter = [").append(toSimpleName(level.getFilter())).append("], ");
                    }
                    if (level.getMaxGroups() >= 0 || level.getPrecision() >= 0) {
                        str.append("max = [").append(level.getMaxGroups()).append(", ")
                           .append(level.getPrecision()).append("], ");
                    }
                    Group group = level.getGroupPrototype();
                    if (!group.getAggregationResults().isEmpty()) {
                        List<String> baz = new LinkedList<>();
                        for (AggregationResult exp : level.getGroupPrototype().getAggregationResults()) {
                            baz.add(toSimpleName(exp));
                        }
                        str.append("result = ").append(baz).append(", ");
                    }
                    if (!group.getOrderByIndexes().isEmpty() || !group.getOrderByExpressions().isEmpty()) {
                        List<String> baz = new LinkedList<>();
                        for (Integer idx : level.getGroupPrototype().getOrderByIndexes()) {
                            baz.add(idx.toString());
                        }
                        str.append("order = [").append(baz).append(", ");
                        baz = new LinkedList<>();
                        for (ExpressionNode exp : level.getGroupPrototype().getOrderByExpressions()) {
                            baz.add(toSimpleName(exp));
                        }
                        str.append(baz).append("], ");
                    }
                    str.setLength(str.length() - 2);
                    str.append(" }");
                    bar.add(str.toString());
                }
                foo.add(bar.toString());
            }
            Collections.sort(foo);
            return foo.toString();
        }

        private static String toSimpleName(ExpressionNode exp) {
            String ret = exp.getClass().getSimpleName();
            if (ret.endsWith("AggregationResult")) {
                return ret.substring(0, ret.length() - 17);
            }
            if (ret.endsWith("FunctionNode")) {
                return ret.substring(0, ret.length() - 12);
            }
            if (ret.endsWith("Node")) {
                return ret.substring(0, ret.length() - 4);
            }
            return ret;
        }

        private static String toSimpleName(FilterExpressionNode filterExp) {
            if (filterExp instanceof RegexPredicateNode rpn) {
                var simpleName = rpn.getExpression().map(LayoutWriter::toSimpleName).orElse("");
                return "Regex [%s]".formatted(simpleName);
            } else if (filterExp instanceof NotPredicateNode npn) {
                var simpleName = npn.getExpression().map(LayoutWriter::toSimpleName).orElse("");
                return "Not [%s]".formatted(simpleName);
            } else if (filterExp instanceof OrPredicateNode opn) {
                var simpleName = opn.getArgs().stream().map(LayoutWriter::toSimpleName).collect(Collectors.joining(", "));
                return "Or [%s]".formatted(simpleName);
            } else if (filterExp instanceof AndPredicateNode apn) {
                var simpleName = apn.getArgs().stream().map(LayoutWriter::toSimpleName).collect(Collectors.joining(", "));
                return "And [%s]".formatted(simpleName);
            }
            return filterExp.getClass().getSimpleName();
        }
    }

    private static class OrderByWriter implements OutputWriter {

        @Override
        public String write(List<Grouping> groupingList, GroupingTransform transform) {
            List<List<String>> ret = new LinkedList<>();
            for (Grouping grouping : groupingList) {
                List<String> lst = new LinkedList<>();
                for (GroupingLevel level : grouping.getLevels()) {
                    lst.add(level.getGroupPrototype().getOrderByIndexes().toString());
                }
                ret.add(lst);
            }
            return ret.toString();
        }
    }

    private static class ForceSinglePassWriter implements OutputWriter {

        @Override
        public String write(List<Grouping> groupingList, GroupingTransform transform) {
            List<String> ret = new LinkedList<>();
            for (Grouping grouping : groupingList) {
                ret.add(String.valueOf(grouping.getForceSinglePass()));
            }
            return ret.toString();
        }
    }
}
