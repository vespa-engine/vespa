// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.vespa;

import com.yahoo.document.GlobalId;
import com.yahoo.document.idstring.IdString;
import com.yahoo.search.grouping.Continuation;
import com.yahoo.search.grouping.request.GroupingOperation;
import com.yahoo.search.grouping.result.AbstractList;
import com.yahoo.search.grouping.result.GroupList;
import com.yahoo.search.grouping.result.HitList;
import com.yahoo.search.result.HitGroup;
import com.yahoo.search.result.Relevance;
import com.yahoo.searchlib.aggregation.*;
import com.yahoo.searchlib.aggregation.hll.SparseSketch;
import com.yahoo.searchlib.expression.*;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * @author Simon Thoresen Hult
 */
public class ResultBuilderTestCase {

    private static final int REQUEST_ID = 0;
    private static final int ROOT_IDX = 0;

    @Test
    void requireThatAllGroupIdsCanBeConverted() {
        assertGroupId("group:6.9", new FloatResultNode(6.9));
        assertGroupId("group:69", new IntegerResultNode(69));
        assertGroupId("group:null", new NullResultNode());
        assertGroupId("group:[6, 9]", new RawResultNode(new byte[]{6, 9}));
        assertGroupId("group:a", new StringResultNode("a"));
        assertGroupId("group:6.9:9.6", new FloatBucketResultNode(6.9, 9.6));
        assertGroupId("group:6:9", new IntegerBucketResultNode(6, 9));
        assertGroupId("group:a:b", new StringBucketResultNode("a", "b"));
        assertGroupId("group:[6, 9]:[9, 6]", new RawBucketResultNode(new RawResultNode(new byte[]{6, 9}),
                new RawResultNode(new byte[]{9, 6})));
    }

    @Test
    void requireThatUnknownGroupIdThrows() {
        assertBuildFail("all(group(a) each(output(count())))",
                Arrays.asList(newGrouping(new Group().setTag(2).setId(new MyResultNode()))),
                "com.yahoo.search.grouping.vespa.ResultBuilderTestCase$MyResultNode");
    }

    @Test
    void requireThatAllExpressionNodesCanBeConverted() {
        assertResult("0", new AverageAggregationResult(new IntegerResultNode(6), 9));
        assertResult("69", new CountAggregationResult(69));
        assertResult("69", new MaxAggregationResult(new IntegerResultNode(69)));
        assertResult("69", new MinAggregationResult(new IntegerResultNode(69)));
        assertResult("69", new SumAggregationResult(new IntegerResultNode(69)));
        assertResult("69", new XorAggregationResult(69));
        assertResult("69", new ExpressionCountAggregationResult(new SparseSketch(), sketch -> 69));
    }

    @Test
    void requireThatUnknownExpressionNodeThrows() {
        assertBuildFail("all(group(a) each(output(count())))",
                Arrays.asList(newGrouping(newGroup(2, 2, new MyAggregationResult().setTag(3)))),
                "com.yahoo.search.grouping.vespa.ResultBuilderTestCase$MyAggregationResult");
    }

    @Test
    void requireThatRootResultsAreIncluded() {
        assertLayout("all(output(count()))",
                new Grouping().setRoot(newGroup(1, new CountAggregationResult(69).setTag(2))),
                "RootGroup{id=group:root, count()=69}[]");
    }

    @Test
    void requireThatRootResultsAreIncludedUsingExpressionCountAggregationResult() {
        assertLayout("all(group(a) output(count()))",
                new Grouping().setRoot(newGroup(1, new ExpressionCountAggregationResult(new SparseSketch(), sketch -> 69).setTag(2))),
                "RootGroup{id=group:root, count()=69}[]");
    }

    @Test
    void requireThatNestedGroupingResultsCanBeTransformed() {
        Grouping grouping = new Grouping()
                .setRoot(new Group()
                        .setTag(1)
                        .addChild(new Group()
                                .setTag(2)
                                .setId(new StringResultNode("foo"))
                                .addAggregationResult(new CountAggregationResult(10).setTag(3))
                                .addChild(new Group()
                                        .setTag(4)
                                        .setId(new StringResultNode("foo_a"))
                                        .addAggregationResult(new CountAggregationResult(15)
                                                .setTag(5)))
                                .addChild(new Group()
                                        .setTag(4)
                                        .setId(new StringResultNode("foo_b"))
                                        .addAggregationResult(new CountAggregationResult(16)
                                                .setTag(5))))
                        .addChild(new Group()
                                .setTag(2)
                                .setId(new StringResultNode("bar"))
                                .addAggregationResult(new CountAggregationResult(20).setTag(3))
                                .addChild(new Group()
                                        .setTag(4)
                                        .setId(new StringResultNode("bar_a"))
                                        .addAggregationResult(
                                                new CountAggregationResult(25)
                                                        .setTag(5)))
                                .addChild(new Group()
                                        .setTag(4)
                                        .setId(new StringResultNode("bar_b"))
                                        .addAggregationResult(
                                                new CountAggregationResult(26)
                                                        .setTag(5)))));
        assertLayout("all(group(artist) max(5) each(output(count() as(baz)) all(group(album) " +
                "max(5) each(output(count() as(cox))) as(group_album))) as(group_artist))",
                grouping,
                "RootGroup{id=group:root}[GroupList{label=group_artist}[" +
                        "Group{id=group:foo, baz=10}[GroupList{label=group_album}[Group{id=group:foo_a, cox=15}[], Group{id=group:foo_b, cox=16}[]]], " +
                        "Group{id=group:bar, baz=20}[GroupList{label=group_album}[Group{id=group:bar_a, cox=25}[], Group{id=group:bar_b, cox=26}[]]]]]");
    }

    @Test
    void requireThatParallelResultsAreTransformed() {
        assertBuild("all(group(foo) each(output(count())) as(bar) each(output(count())) as(baz))",
                Arrays.asList(new Grouping().setRoot(newGroup(1, 0)),
                        new Grouping().setRoot(newGroup(1, 0))));
        assertBuildFail("all(group(foo) each(output(count())) as(bar) each(output(count())) as(baz))",
                Arrays.asList(new Grouping().setRoot(newGroup(2)),
                        new Grouping().setRoot(newGroup(3))),
                "Expected 1 group, got 2.");
    }

    @Test
    void requireThatTagsAreHandledCorrectly() {
        assertBuild("all(group(a) each(output(count())))",
                Arrays.asList(newGrouping(
                        newGroup(7, new CountAggregationResult(0)))));
    }

    @Test
    void requireThatEmptyBranchesArePruned() {
        assertBuildFail("all()", Collections.<Grouping>emptyList(), "Expected 1 group, got 0.");
        assertBuildFail("all(group(a))", Collections.<Grouping>emptyList(), "Expected 1 group, got 0.");
        assertBuildFail("all(group(a) each())", Collections.<Grouping>emptyList(), "Expected 1 group, got 0.");

        Grouping grouping = newGrouping(newGroup(2, new CountAggregationResult(69).setTag(3)));
        String expectedOutput = "RootGroup{id=group:root}[GroupList{label=a}[Group{id=group:2, count()=69}[]]]";
        assertLayout("all(group(a) each(output(count())))", grouping, expectedOutput);
        assertLayout("all(group(a) each(output(count()) all()))", grouping, expectedOutput);
        assertLayout("all(group(a) each(output(count()) all(group(b))))", grouping, expectedOutput);
        assertLayout("all(group(a) each(output(count()) all(group(b) each())))", grouping, expectedOutput);
        assertLayout("all(group(a) each(output(count()) all(group(b) each())))", grouping, expectedOutput);
        assertLayout("all(group(a) each(output(count()) all(group(b) each()))" +
                "             each() as(foo))", grouping, expectedOutput);
        assertLayout("all(group(a) each(output(count()) all(group(b) each()))" +
                "             each(group(b)) as(foo))", grouping, expectedOutput);
        assertLayout("all(group(a) each(output(count()) all(group(b) each()))" +
                "             each(group(b) each()) as(foo))", grouping, expectedOutput);
    }

    @Test
    void requireThatGroupListsAreLabeled() {
        assertLayout("all(group(a) each(output(count())))",
                newGrouping(newGroup(2, new CountAggregationResult(69).setTag(3))),
                "RootGroup{id=group:root}[GroupList{label=a}[Group{id=group:2, count()=69}[]]]");
        assertLayout("all(group(a) each(output(count())) as(bar))",
                newGrouping(newGroup(2, new CountAggregationResult(69).setTag(3))),
                "RootGroup{id=group:root}[GroupList{label=bar}[Group{id=group:2, count()=69}[]]]");
    }

    @Test
    void requireThatHitListsAreLabeled() {
        assertLayout("all(group(foo) each(each(output(summary()))))",
                newGrouping(newGroup(2, newHitList(3, 2))),
                "RootGroup{id=group:root}[GroupList{label=foo}[Group{id=group:2}[" +
                        "HitList{label=hits}[Hit{id=hit:1}, Hit{id=hit:2}]]]]");
        assertLayout("all(group(foo) each(each(output(summary())) as(bar)))",
                newGrouping(newGroup(2, newHitList(3, 2))),
                "RootGroup{id=group:root}[GroupList{label=foo}[Group{id=group:2}[" +
                        "HitList{label=bar}[Hit{id=hit:1}, Hit{id=hit:2}]]]]");
        assertLayout("all(group(foo) each(each(output(summary())) as(bar)) as(baz))",
                newGrouping(newGroup(2, newHitList(3, 2))),
                "RootGroup{id=group:root}[GroupList{label=baz}[Group{id=group:2}[" +
                        "HitList{label=bar}[Hit{id=hit:1}, Hit{id=hit:2}]]]]");
        assertLayout("all(group(foo) each(each(output(summary())) as(bar)" +
                "                    each(output(summary())) as(baz)))",
                Arrays.asList(newGrouping(newGroup(2, newHitList(3, 2))),
                        newGrouping(newGroup(2, newHitList(4, 2)))),
                "RootGroup{id=group:root}[GroupList{label=foo}[Group{id=group:2}[" +
                        "HitList{label=bar}[Hit{id=hit:1}, Hit{id=hit:2}], " +
                        "HitList{label=baz}[Hit{id=hit:1}, Hit{id=hit:2}]]]]");
        assertLayout("all(group(foo) each(each(output(summary())))" +
                "               each(each(output(summary()))) as(bar))",
                Arrays.asList(newGrouping(newGroup(2, newHitList(3, 2))),
                        newGrouping(newGroup(4, newHitList(5, 2)))),
                "RootGroup{id=group:root}[" +
                        "GroupList{label=foo}[Group{id=group:2}[HitList{label=hits}[Hit{id=hit:1}, Hit{id=hit:2}]]], " +
                        "GroupList{label=bar}[Group{id=group:4}[HitList{label=hits}[Hit{id=hit:1}, Hit{id=hit:2}]]]]");
    }

    @Test
    void requireThatOutputsAreLabeled() {
        assertLayout("all(output(count()))",
                new Grouping().setRoot(newGroup(1, new CountAggregationResult(69).setTag(2))),
                "RootGroup{id=group:root, count()=69}[]");
        assertLayout("all(output(count() as(foo)))",
                new Grouping().setRoot(newGroup(1, new CountAggregationResult(69).setTag(2))),
                "RootGroup{id=group:root, foo=69}[]");
        assertLayout("all(group(a) each(output(count())))",
                newGrouping(newGroup(2, new CountAggregationResult(69).setTag(3))),
                "RootGroup{id=group:root}[GroupList{label=a}[Group{id=group:2, count()=69}[]]]");
        assertLayout("all(group(a) each(output(count() as(foo))))",
                newGrouping(newGroup(2, new CountAggregationResult(69).setTag(3))),
                "RootGroup{id=group:root}[GroupList{label=a}[Group{id=group:2, foo=69}[]]]");
    }

    @Test
    void requireThatExpressionCountCanUseExactGroupCount() {
        Group root1 = newGroup(1, new ExpressionCountAggregationResult(new SparseSketch(), sketch -> 42).setTag(2));
        Grouping grouping1 = new Grouping().setRoot(root1);

        // Should return estimate when no groups are returned (since each() clause is absent).
        assertLayout("all(group(artist) output(count()))",
                grouping1,
                "RootGroup{id=group:root, count()=42}[]");

        Group root2 = newGroup(1, new ExpressionCountAggregationResult(new SparseSketch(), sketch -> 42).setTag(2));
        Grouping grouping2 = new Grouping().setRoot(root2);
        for (int i = 0; i < 3; ++i) {
            root2.addChild(new Group()
                    .setTag(2)
                    .setId(new StringResultNode("foo" + i)))
                    .addAggregationResult(new CountAggregationResult(i).setTag(3));
        }

        // Should return the number of groups when max is not present.
        assertLayout("all(group(artist) output(count()) each(output(count())))",
                grouping2,
                "RootGroup{id=group:root, count()=3, artist=2}" +
                        "[GroupList{label=count()}[Group{id=group:foo0}[], Group{id=group:foo1}[], Group{id=group:foo2}[]]]");

        // Should return the number of groups when max is higher than group count.
        assertLayout("all(group(artist) max(5) output(count()) each(output(count())))",
                grouping2,
                "RootGroup{id=group:root, count()=3, artist=2}" +
                        "[GroupList{label=count()}[Group{id=group:foo0}[], Group{id=group:foo1}[], Group{id=group:foo2}[]]]");

        // Should return the estimate when number of groups is equal to max.
        assertLayout("all(group(artist) max(3) output(count()) each(output(count())))",
                grouping2,
                "RootGroup{id=group:root, count()=42, artist=2}" +
                        "[GroupList{label=count()}[Group{id=group:foo0}[], Group{id=group:foo1}[], Group{id=group:foo2}[]]]");

    }


    @Test
    void requireThatResultContinuationContainsCurrentPages() {
        String request = "all(group(a) max(2) each(output(count())))";
        Grouping result = newGrouping(newGroup(2, 1, new CountAggregationResult(1)),
                newGroup(2, 2, new CountAggregationResult(2)),
                newGroup(2, 3, new CountAggregationResult(3)),
                newGroup(2, 4, new CountAggregationResult(4)));
        assertResultCont(request, result, newOffset(newResultId(0), 2, 0), "[]");
        assertResultCont(request, result, newOffset(newResultId(0), 2, 1), "[0=1]");
        assertResultCont(request, result, newOffset(newResultId(0), 2, 2), "[0=2]");
        assertResultCont(request, result, newOffset(newResultId(0), 2, 3), "[0=3]");

        assertResultCont("all(group(a) max(2) each(output(count())) as(foo)" +
                "                    each(output(count())) as(bar))",
                Arrays.asList(newGrouping(newGroup(2, 1, new CountAggregationResult(1))),
                        newGrouping(newGroup(4, 2, new CountAggregationResult(4)))),
                "[]");
        assertResultCont("all(group(a) max(2) each(output(count())) as(foo)" +
                "                    each(output(count())) as(bar))",
                Arrays.asList(newGrouping(newGroup(2, 1, new CountAggregationResult(1))),
                        newGrouping(newGroup(4, 2, new CountAggregationResult(4)))),
                newOffset(newResultId(0), 2, 1),
                "[0=1]");
        assertResultCont("all(group(a) max(2) each(output(count())) as(foo)" +
                "                    each(output(count())) as(bar))",
                Arrays.asList(newGrouping(newGroup(2, 1, new CountAggregationResult(1))),
                        newGrouping(newGroup(4, 2, new CountAggregationResult(4)))),
                newComposite(newOffset(newResultId(0), 2, 2),
                        newOffset(newResultId(1), 4, 1)),
                "[0=2, 1=1]");

        request = "all(group(a) each(max(2) each(output(summary()))))";
        result = newGrouping(newGroup(2, newHitList(3, 4)));
        assertResultCont(request, result, newOffset(newResultId(0, 0, 0), 3, 0), "[]");
        assertResultCont(request, result, newOffset(newResultId(0, 0, 0), 3, 1), "[0.0.0=1]");
        assertResultCont(request, result, newOffset(newResultId(0, 0, 0), 3, 2), "[0.0.0=2]");
        assertResultCont(request, result, newOffset(newResultId(0, 0, 0), 3, 3), "[0.0.0=3]");

        assertResultCont("all(group(a) each(max(2) each(output(summary()))) as(foo)" +
                "             each(max(2) each(output(summary()))) as(bar))",
                Arrays.asList(newGrouping(newGroup(2, newHitList(3, 4))),
                        newGrouping(newGroup(4, newHitList(5, 4)))),
                "[]");
        assertResultCont("all(group(a) each(max(2) each(output(summary()))) as(foo)" +
                "             each(max(2) each(output(summary()))) as(bar))",
                Arrays.asList(newGrouping(newGroup(2, newHitList(3, 4))),
                        newGrouping(newGroup(4, newHitList(5, 4)))),
                newOffset(newResultId(0, 0, 0), 3, 1),
                "[0.0.0=1]");
        assertResultCont("all(group(a) each(max(2) each(output(summary()))) as(foo)" +
                "             each(max(2) each(output(summary()))) as(bar))",
                Arrays.asList(newGrouping(newGroup(2, newHitList(3, 4))),
                        newGrouping(newGroup(4, newHitList(5, 4)))),
                newComposite(newOffset(newResultId(0, 0, 0), 3, 2),
                        newOffset(newResultId(1, 0, 0), 5, 1)),
                "[0.0.0=2, 1.0.0=1]");
    }

    @Test
    void requireThatGroupListContinuationsAreNotCreatedWhenUnlessMaxIsSet() {
        assertContinuation("all(group(a) each(output(count())))",
                newGrouping(newGroup(2, 1, new CountAggregationResult(1)),
                        newGroup(2, 2, new CountAggregationResult(2)),
                        newGroup(2, 3, new CountAggregationResult(3)),
                        newGroup(2, 4, new CountAggregationResult(4))),
                "{ 'group:root', {}, [{ 'grouplist:a', {}, [" +
                        "{ 'group:1', {}, [] }, { 'group:2', {}, [] }, { 'group:3', {}, [] }, { 'group:4', {}, [] }] }] }");
    }

    @Test
    void requireThatGroupListContinuationsCanBeSet() {
        String request = "all(group(a) max(2) each(output(count())))";
        Grouping result = newGrouping(newGroup(2, 1, new CountAggregationResult(1)),
                newGroup(2, 2, new CountAggregationResult(2)),
                newGroup(2, 3, new CountAggregationResult(3)),
                newGroup(2, 4, new CountAggregationResult(4)));
        assertContinuation(request, result, newOffset(newResultId(0), 2, 0),
                "{ 'group:root', {}, [{ 'grouplist:a', {next=2}, [" +
                        "{ 'group:1', {}, [] }, { 'group:2', {}, [] }] }] }");
        assertContinuation(request, result, newOffset(newResultId(0), 2, 1),
                "{ 'group:root', {}, [{ 'grouplist:a', {next=3, prev=0}, [" +
                        "{ 'group:2', {}, [] }, { 'group:3', {}, [] }] }] }");
        assertContinuation(request, result, newOffset(newResultId(0), 2, 2),
                "{ 'group:root', {}, [{ 'grouplist:a', {prev=0}, [" +
                        "{ 'group:3', {}, [] }, { 'group:4', {}, [] }] }] }");
        assertContinuation(request, result, newOffset(newResultId(0), 2, 3),
                "{ 'group:root', {}, [{ 'grouplist:a', {prev=1}, [" +
                        "{ 'group:4', {}, [] }] }] }");
        assertContinuation(request, result, newOffset(newResultId(0), 2, 4),
                "{ 'group:root', {}, [{ 'grouplist:a', {prev=2}, [" +
                        "] }] }");
        assertContinuation(request, result, newOffset(newResultId(0), 2, 5),
                "{ 'group:root', {}, [{ 'grouplist:a', {prev=2}, [" +
                        "] }] }");
    }

    @Test
    void requireThatGroupListContinuationsCanBeSetInSiblingGroups() {
        String request = "all(group(a) each(group(b) max(2) each(output(count()))))";
        Grouping result = newGrouping(newGroup(2, 201,
                newGroup(3, 301, new CountAggregationResult(1)),
                newGroup(3, 302, new CountAggregationResult(2)),
                newGroup(3, 303, new CountAggregationResult(3)),
                newGroup(3, 304, new CountAggregationResult(4))),
                newGroup(2, 202,
                        newGroup(3, 305, new CountAggregationResult(5)),
                        newGroup(3, 306, new CountAggregationResult(6)),
                        newGroup(3, 307, new CountAggregationResult(7)),
                        newGroup(3, 308, new CountAggregationResult(8))));
        assertContinuation(request, result, newComposite(newOffset(newResultId(0, 0, 0), 2, 0),
                newOffset(newResultId(0, 1, 0), 2, 5)),
                "{ 'group:root', {}, [{ 'grouplist:a', {}, [" +
                        "{ 'group:201', {}, [{ 'grouplist:b', {next=2}, [{ 'group:301', {}, [] }, { 'group:302', {}, [] }] }] }, " +
                        "{ 'group:202', {}, [{ 'grouplist:b', {prev=2}, [] }] }] }] }");
        assertContinuation(request, result, newComposite(newOffset(newResultId(0, 0, 0), 2, 1),
                newOffset(newResultId(0, 1, 0), 2, 4)),
                "{ 'group:root', {}, [{ 'grouplist:a', {}, [" +
                        "{ 'group:201', {}, [{ 'grouplist:b', {next=3, prev=0}, [{ 'group:302', {}, [] }, { 'group:303', {}, [] }] }] }, " +
                        "{ 'group:202', {}, [{ 'grouplist:b', {prev=2}, [] }] }] }] }");
        assertContinuation(request, result, newComposite(newOffset(newResultId(0, 0, 0), 2, 2),
                newOffset(newResultId(0, 1, 0), 2, 3)),
                "{ 'group:root', {}, [{ 'grouplist:a', {}, [" +
                        "{ 'group:201', {}, [{ 'grouplist:b', {prev=0}, [{ 'group:303', {}, [] }, { 'group:304', {}, [] }] }] }, " +
                        "{ 'group:202', {}, [{ 'grouplist:b', {prev=1}, [{ 'group:308', {}, [] }] }] }] }] }");
        assertContinuation(request, result, newComposite(newOffset(newResultId(0, 0, 0), 2, 3),
                newOffset(newResultId(0, 1, 0), 2, 2)),
                "{ 'group:root', {}, [{ 'grouplist:a', {}, [" +
                        "{ 'group:201', {}, [{ 'grouplist:b', {prev=1}, [{ 'group:304', {}, [] }] }] }, " +
                        "{ 'group:202', {}, [{ 'grouplist:b', {prev=0}, [{ 'group:307', {}, [] }, { 'group:308', {}, [] }] }] }] }] }");
        assertContinuation(request, result, newComposite(newOffset(newResultId(0, 0, 0), 2, 4),
                newOffset(newResultId(0, 1, 0), 2, 1)),
                "{ 'group:root', {}, [{ 'grouplist:a', {}, [" +
                        "{ 'group:201', {}, [{ 'grouplist:b', {prev=2}, [] }] }, " +
                        "{ 'group:202', {}, [{ 'grouplist:b', {next=3, prev=0}, [{ 'group:306', {}, [] }, { 'group:307', {}, [] }] }] }] }] }");
        assertContinuation(request, result, newComposite(newOffset(newResultId(0, 0, 0), 2, 5),
                newOffset(newResultId(0, 1, 0), 2, 0)),
                "{ 'group:root', {}, [{ 'grouplist:a', {}, [" +
                        "{ 'group:201', {}, [{ 'grouplist:b', {prev=2}, [] }] }, " +
                        "{ 'group:202', {}, [{ 'grouplist:b', {next=2}, [{ 'group:305', {}, [] }, { 'group:306', {}, [] }] }] }] }] }");
    }

    @Test
    void requireThatGroupListContinuationsCanBeSetInSiblingGroupLists() {
        String request = "all(group(a) max(2) each(output(count())) as(foo)" +
                "                    each(output(count())) as(bar))";
        List<Grouping> result = Arrays.asList(newGrouping(newGroup(2, 1, new CountAggregationResult(1)),
                newGroup(2, 2, new CountAggregationResult(2)),
                newGroup(2, 3, new CountAggregationResult(3)),
                newGroup(2, 4, new CountAggregationResult(4))),
                newGrouping(newGroup(4, 1, new CountAggregationResult(1)),
                        newGroup(4, 2, new CountAggregationResult(2)),
                        newGroup(4, 3, new CountAggregationResult(3)),
                        newGroup(4, 4, new CountAggregationResult(4))));
        assertContinuation(request, result, newComposite(newOffset(newResultId(0), 2, 0),
                newOffset(newResultId(1), 4, 5)),
                "{ 'group:root', {}, [" +
                        "{ 'grouplist:foo', {next=2}, [{ 'group:1', {}, [] }, { 'group:2', {}, [] }] }, " +
                        "{ 'grouplist:bar', {prev=2}, [] }] }");
        assertContinuation(request, result, newComposite(newOffset(newResultId(0), 2, 1),
                newOffset(newResultId(1), 4, 4)),
                "{ 'group:root', {}, [" +
                        "{ 'grouplist:foo', {next=3, prev=0}, [{ 'group:2', {}, [] }, { 'group:3', {}, [] }] }, " +
                        "{ 'grouplist:bar', {prev=2}, [] }] }");
        assertContinuation(request, result, newComposite(newOffset(newResultId(0), 2, 2),
                newOffset(newResultId(1), 4, 3)),
                "{ 'group:root', {}, [" +
                        "{ 'grouplist:foo', {prev=0}, [{ 'group:3', {}, [] }, { 'group:4', {}, [] }] }, " +
                        "{ 'grouplist:bar', {prev=1}, [{ 'group:4', {}, [] }] }] }");
        assertContinuation(request, result, newComposite(newOffset(newResultId(0), 2, 3),
                newOffset(newResultId(1), 4, 2)),
                "{ 'group:root', {}, [" +
                        "{ 'grouplist:foo', {prev=1}, [{ 'group:4', {}, [] }] }, " +
                        "{ 'grouplist:bar', {prev=0}, [{ 'group:3', {}, [] }, { 'group:4', {}, [] }] }] }");
        assertContinuation(request, result, newComposite(newOffset(newResultId(0), 2, 4),
                newOffset(newResultId(1), 4, 1)),
                "{ 'group:root', {}, [" +
                        "{ 'grouplist:foo', {prev=2}, [] }, " +
                        "{ 'grouplist:bar', {next=3, prev=0}, [{ 'group:2', {}, [] }, { 'group:3', {}, [] }] }] }");
        assertContinuation(request, result, newComposite(newOffset(newResultId(0), 2, 5),
                newOffset(newResultId(1), 4, 0)),
                "{ 'group:root', {}, [" +
                        "{ 'grouplist:foo', {prev=2}, [] }, " +
                        "{ 'grouplist:bar', {next=2}, [{ 'group:1', {}, [] }, { 'group:2', {}, [] }] }] }");
    }

    @Test
    void requireThatUnstableContinuationsDoNotAffectSiblingGroupLists() {
        String request = "all(group(a) each(group(b) max(2) each(group(c) max(2) each(output(count())))))";
        Grouping result = newGrouping(newGroup(2, 201,
                newGroup(3, 301,
                        newGroup(4, 401, new CountAggregationResult(1)),
                        newGroup(4, 402, new CountAggregationResult(1)),
                        newGroup(4, 403, new CountAggregationResult(1)),
                        newGroup(4, 404, new CountAggregationResult(1))),
                newGroup(3, 302,
                        newGroup(4, 405, new CountAggregationResult(1)),
                        newGroup(4, 406, new CountAggregationResult(1)),
                        newGroup(4, 407, new CountAggregationResult(1)),
                        newGroup(4, 408, new CountAggregationResult(1))),
                newGroup(3, 303,
                        newGroup(4, 409, new CountAggregationResult(1)),
                        newGroup(4, 410, new CountAggregationResult(1)),
                        newGroup(4, 411, new CountAggregationResult(1)),
                        newGroup(4, 412, new CountAggregationResult(1))),
                newGroup(3, 304,
                        newGroup(4, 413, new CountAggregationResult(1)),
                        newGroup(4, 414, new CountAggregationResult(1)),
                        newGroup(4, 415, new CountAggregationResult(1)),
                        newGroup(4, 416, new CountAggregationResult(1)))),
                newGroup(2, 202,
                        newGroup(3, 305,
                                newGroup(4, 417, new CountAggregationResult(1)),
                                newGroup(4, 418, new CountAggregationResult(1)),
                                newGroup(4, 419, new CountAggregationResult(1)),
                                newGroup(4, 420, new CountAggregationResult(1))),
                        newGroup(3, 306,
                                newGroup(4, 421, new CountAggregationResult(1)),
                                newGroup(4, 422, new CountAggregationResult(1)),
                                newGroup(4, 423, new CountAggregationResult(1)),
                                newGroup(4, 424, new CountAggregationResult(1))),
                        newGroup(3, 307,
                                newGroup(4, 425, new CountAggregationResult(1)),
                                newGroup(4, 426, new CountAggregationResult(1)),
                                newGroup(4, 427, new CountAggregationResult(1)),
                                newGroup(4, 428, new CountAggregationResult(1))),
                        newGroup(3, 308,
                                newGroup(4, 429, new CountAggregationResult(1)),
                                newGroup(4, 430, new CountAggregationResult(1)),
                                newGroup(4, 431, new CountAggregationResult(1)),
                                newGroup(4, 432, new CountAggregationResult(1)))));
        assertContinuation(request, result, newComposite(),
                "{ 'group:root', {}, [{ 'grouplist:a', {}, [" +
                        "{ 'group:201', {}, [{ 'grouplist:b', {next=2}, [" +
                        "{ 'group:301', {}, [{ 'grouplist:c', {next=2}, [{ 'group:401', {}, [] }, { 'group:402', {}, [] }] }] }, " +
                        "{ 'group:302', {}, [{ 'grouplist:c', {next=2}, [{ 'group:405', {}, [] }, { 'group:406', {}, [] }] }] }] }] }, " +
                        "{ 'group:202', {}, [{ 'grouplist:b', {next=2}, [" +
                        "{ 'group:305', {}, [{ 'grouplist:c', {next=2}, [{ 'group:417', {}, [] }, { 'group:418', {}, [] }] }] }, " +
                        "{ 'group:306', {}, [{ 'grouplist:c', {next=2}, [{ 'group:421', {}, [] }, { 'group:422', {}, [] }] }] }] }] }] }] }");
        assertContinuation(request, result, newComposite(newOffset(newResultId(0, 1, 0, 1, 0), 4, 2)),
                "{ 'group:root', {}, [{ 'grouplist:a', {}, [" +
                        "{ 'group:201', {}, [{ 'grouplist:b', {next=2}, [" +
                        "{ 'group:301', {}, [{ 'grouplist:c', {next=2}, [{ 'group:401', {}, [] }, { 'group:402', {}, [] }] }] }, " +
                        "{ 'group:302', {}, [{ 'grouplist:c', {next=2}, [{ 'group:405', {}, [] }, { 'group:406', {}, [] }] }] }] }] }, " +
                        "{ 'group:202', {}, [{ 'grouplist:b', {next=2}, [" +
                        "{ 'group:305', {}, [{ 'grouplist:c', {next=2}, [{ 'group:417', {}, [] }, { 'group:418', {}, [] }] }] }, " +
                        "{ 'group:306', {}, [{ 'grouplist:c', {prev=0}, [{ 'group:423', {}, [] }, { 'group:424', {}, [] }] }] }] }] }] }] }");
        assertContinuation(request, result, newComposite(newOffset(newResultId(0, 1, 0, 1, 0), 4, 2),
                newOffset(newResultId(0, 0, 0), 2, 2)),
                "{ 'group:root', {}, [{ 'grouplist:a', {}, [" +
                        "{ 'group:201', {}, [{ 'grouplist:b', {prev=0}, [" +
                        "{ 'group:303', {}, [{ 'grouplist:c', {next=2}, [{ 'group:409', {}, [] }, { 'group:410', {}, [] }] }] }, " +
                        "{ 'group:304', {}, [{ 'grouplist:c', {next=2}, [{ 'group:413', {}, [] }, { 'group:414', {}, [] }] }] }] }] }, " +
                        "{ 'group:202', {}, [{ 'grouplist:b', {next=2}, [" +
                        "{ 'group:305', {}, [{ 'grouplist:c', {next=2}, [{ 'group:417', {}, [] }, { 'group:418', {}, [] }] }] }, " +
                        "{ 'group:306', {}, [{ 'grouplist:c', {prev=0}, [{ 'group:423', {}, [] }, { 'group:424', {}, [] }] }] }] }] }] }] }");
        assertContinuation(request, result, newComposite(newOffset(newResultId(0, 1, 0, 1, 0), 4, 2),
                newOffset(newResultId(0, 0, 0), 2, 2),
                newUnstableOffset(newResultId(0, 1, 0), 2, 1)),
                "{ 'group:root', {}, [{ 'grouplist:a', {}, [" +
                        "{ 'group:201', {}, [{ 'grouplist:b', {prev=0}, [" +
                        "{ 'group:303', {}, [{ 'grouplist:c', {next=2}, [{ 'group:409', {}, [] }, { 'group:410', {}, [] }] }] }, " +
                        "{ 'group:304', {}, [{ 'grouplist:c', {next=2}, [{ 'group:413', {}, [] }, { 'group:414', {}, [] }] }] }] }] }, " +
                        "{ 'group:202', {}, [{ 'grouplist:b', {next=3, prev=0}, [" +
                        "{ 'group:306', {}, [{ 'grouplist:c', {next=2}, [{ 'group:421', {}, [] }, { 'group:422', {}, [] }] }] }, " +
                        "{ 'group:307', {}, [{ 'grouplist:c', {next=2}, [{ 'group:425', {}, [] }, { 'group:426', {}, [] }] }] }] }] }] }] }");
    }

    @Test
    void requireThatUnstableContinuationsAffectAllDecendants() {
        String request = "all(group(a) each(group(b) max(1) each(group(c) max(1) each(group(d) max(1) each(output(count()))))))";
        Grouping result = newGrouping(newGroup(2, 201,
                newGroup(3, 301,
                        newGroup(4, 401,
                                newGroup(5, 501, new CountAggregationResult(1)),
                                newGroup(5, 502, new CountAggregationResult(1))),
                        newGroup(4, 402,
                                newGroup(5, 503, new CountAggregationResult(1)),
                                newGroup(5, 504, new CountAggregationResult(1)))),
                newGroup(3, 302,
                        newGroup(4, 403,
                                newGroup(5, 505, new CountAggregationResult(1)),
                                newGroup(5, 506, new CountAggregationResult(1))),
                        newGroup(4, 404,
                                newGroup(5, 507, new CountAggregationResult(1)),
                                newGroup(5, 508, new CountAggregationResult(1))))));
        assertContinuation(request, result, newComposite(),
                "{ 'group:root', {}, [{ 'grouplist:a', {}, [" +
                        "{ 'group:201', {}, [{ 'grouplist:b', {next=1}, [" +
                        "{ 'group:301', {}, [{ 'grouplist:c', {next=1}, [" +
                        "{ 'group:401', {}, [{ 'grouplist:d', {next=1}, [" +
                        "{ 'group:501', {}, [] }] }] }] }] }] }] }] }] }");
        assertContinuation(request, result, newComposite(newOffset(newResultId(0, 0, 0, 0, 0, 0, 0), 5, 1)),
                "{ 'group:root', {}, [{ 'grouplist:a', {}, [" +
                        "{ 'group:201', {}, [{ 'grouplist:b', {next=1}, [" +
                        "{ 'group:301', {}, [{ 'grouplist:c', {next=1}, [" +
                        "{ 'group:401', {}, [{ 'grouplist:d', {prev=0}, [" +
                        "{ 'group:502', {}, [] }] }] }] }] }] }] }] }] }");
        assertContinuation(request, result, newComposite(newOffset(newResultId(0, 0, 0, 0, 0, 0, 0), 5, 1),
                newUnstableOffset(newResultId(0, 0, 0, 0, 0), 4, 1)),
                "{ 'group:root', {}, [{ 'grouplist:a', {}, [" +
                        "{ 'group:201', {}, [{ 'grouplist:b', {next=1}, [" +
                        "{ 'group:301', {}, [{ 'grouplist:c', {prev=0}, [" +
                        "{ 'group:402', {}, [{ 'grouplist:d', {next=1}, [" +
                        "{ 'group:503', {}, [] }] }] }] }] }] }] }] }] }");
        assertContinuation(request, result, newComposite(newOffset(newResultId(0, 0, 0, 0, 0, 0, 0), 5, 1),
                newUnstableOffset(newResultId(0, 0, 0, 0, 0), 4, 1),
                newUnstableOffset(newResultId(0, 0, 0), 3, 1)),
                "{ 'group:root', {}, [{ 'grouplist:a', {}, [" +
                        "{ 'group:201', {}, [{ 'grouplist:b', {prev=0}, [" +
                        "{ 'group:302', {}, [{ 'grouplist:c', {next=1}, [" +
                        "{ 'group:403', {}, [{ 'grouplist:d', {next=1}, [" +
                        "{ 'group:505', {}, [] }] }] }] }] }] }] }] }] }");
    }

    @Test
    void requireThatHitListContinuationsAreNotCreatedUnlessMaxIsSet() {
        assertContinuation("all(group(a) each(each(output(summary()))))",
                newGrouping(newGroup(2, newHitList(3, 4))),
                "{ 'group:root', {}, [{ 'grouplist:a', {}, [" +
                        "{ 'group:2', {}, [{ 'hitlist:hits', {}, [hit:1, hit:2, hit:3, hit:4] }] }] }] }");
    }

    @Test
    void requireThatHitListContinuationsCanBeSet() {
        String request = "all(group(a) each(max(2) each(output(summary()))))";
        Grouping result = newGrouping(newGroup(2, newHitList(3, 4)));
        assertContinuation(request, result, newOffset(newResultId(0, 0, 0), 3, 0),
                "{ 'group:root', {}, [{ 'grouplist:a', {}, [" +
                        "{ 'group:2', {}, [{ 'hitlist:hits', {next=2}, [hit:1, hit:2] }] }] }] }");
        assertContinuation(request, result, newOffset(newResultId(0, 0, 0), 3, 1),
                "{ 'group:root', {}, [{ 'grouplist:a', {}, [" +
                        "{ 'group:2', {}, [{ 'hitlist:hits', {next=3, prev=0}, [hit:2, hit:3] }] }] }] }");
        assertContinuation(request, result, newOffset(newResultId(0, 0, 0), 3, 2),
                "{ 'group:root', {}, [{ 'grouplist:a', {}, [" +
                        "{ 'group:2', {}, [{ 'hitlist:hits', {prev=0}, [hit:3, hit:4] }] }] }] }");
        assertContinuation(request, result, newOffset(newResultId(0, 0, 0), 3, 3),
                "{ 'group:root', {}, [{ 'grouplist:a', {}, [" +
                        "{ 'group:2', {}, [{ 'hitlist:hits', {prev=1}, [hit:4] }] }] }] }");
        assertContinuation(request, result, newOffset(newResultId(0, 0, 0), 3, 4),
                "{ 'group:root', {}, [{ 'grouplist:a', {}, [" +
                        "{ 'group:2', {}, [{ 'hitlist:hits', {prev=2}, [] }] }] }] }");
        assertContinuation(request, result, newOffset(newResultId(0, 0, 0), 3, 5),
                "{ 'group:root', {}, [{ 'grouplist:a', {}, [" +
                        "{ 'group:2', {}, [{ 'hitlist:hits', {prev=2}, [] }] }] }] }");
    }

    @Test
    void requireThatHitListContinuationsCanBeSetInSiblingGroups() {
        String request = "all(group(a) each(max(2) each(output(summary()))))";
        Grouping result = newGrouping(newGroup(2, 201, newHitList(3, 4)),
                newGroup(2, 202, newHitList(3, 4)));
        assertContinuation(request, result, newComposite(newOffset(newResultId(0, 0, 0), 3, 0),
                newOffset(newResultId(0, 1, 0), 3, 5)),
                "{ 'group:root', {}, [{ 'grouplist:a', {}, [" +
                        "{ 'group:201', {}, [{ 'hitlist:hits', {next=2}, [hit:1, hit:2] }] }, " +
                        "{ 'group:202', {}, [{ 'hitlist:hits', {prev=2}, [] }] }] }] }");
        assertContinuation(request, result, newComposite(newOffset(newResultId(0, 0, 0), 3, 1),
                newOffset(newResultId(0, 1, 0), 3, 4)),
                "{ 'group:root', {}, [{ 'grouplist:a', {}, [" +
                        "{ 'group:201', {}, [{ 'hitlist:hits', {next=3, prev=0}, [hit:2, hit:3] }] }, " +
                        "{ 'group:202', {}, [{ 'hitlist:hits', {prev=2}, [] }] }] }] }");
        assertContinuation(request, result, newComposite(newOffset(newResultId(0, 0, 0), 3, 2),
                newOffset(newResultId(0, 1, 0), 3, 3)),
                "{ 'group:root', {}, [{ 'grouplist:a', {}, [" +
                        "{ 'group:201', {}, [{ 'hitlist:hits', {prev=0}, [hit:3, hit:4] }] }, " +
                        "{ 'group:202', {}, [{ 'hitlist:hits', {prev=1}, [hit:4] }] }] }] }");
        assertContinuation(request, result, newComposite(newOffset(newResultId(0, 0, 0), 3, 3),
                newOffset(newResultId(0, 1, 0), 3, 2)),
                "{ 'group:root', {}, [{ 'grouplist:a', {}, [" +
                        "{ 'group:201', {}, [{ 'hitlist:hits', {prev=1}, [hit:4] }] }, " +
                        "{ 'group:202', {}, [{ 'hitlist:hits', {prev=0}, [hit:3, hit:4] }] }] }] }");
        assertContinuation(request, result, newComposite(newOffset(newResultId(0, 0, 0), 3, 4),
                newOffset(newResultId(0, 1, 0), 3, 1)),
                "{ 'group:root', {}, [{ 'grouplist:a', {}, [" +
                        "{ 'group:201', {}, [{ 'hitlist:hits', {prev=2}, [] }] }, " +
                        "{ 'group:202', {}, [{ 'hitlist:hits', {next=3, prev=0}, [hit:2, hit:3] }] }] }] }");
        assertContinuation(request, result, newComposite(newOffset(newResultId(0, 0, 0), 3, 5),
                newOffset(newResultId(0, 1, 0), 3, 0)),
                "{ 'group:root', {}, [{ 'grouplist:a', {}, [" +
                        "{ 'group:201', {}, [{ 'hitlist:hits', {prev=2}, [] }] }, " +
                        "{ 'group:202', {}, [{ 'hitlist:hits', {next=2}, [hit:1, hit:2] }] }] }] }");
    }

    @Test
    void requireThatHitListContinuationsCanBeSetInSiblingHitLists() {
        String request = "all(group(a) each(max(2) each(output(summary()))) as(foo)" +
                "             each(max(2) each(output(summary()))) as(bar))";
        List<Grouping> result = Arrays.asList(newGrouping(newGroup(2, newHitList(3, 4))),
                newGrouping(newGroup(4, newHitList(5, 4))));
        assertContinuation(request, result, newComposite(newOffset(newResultId(0, 0, 0), 3, 0),
                newOffset(newResultId(1, 0, 0), 5, 5)),
                "{ 'group:root', {}, [" +
                        "{ 'grouplist:foo', {}, [{ 'group:2', {}, [{ 'hitlist:hits', {next=2}, [hit:1, hit:2] }] }] }, " +
                        "{ 'grouplist:bar', {}, [{ 'group:4', {}, [{ 'hitlist:hits', {prev=2}, [] }] }] }] }");
        assertContinuation(request, result, newComposite(newOffset(newResultId(0, 0, 0), 3, 1),
                newOffset(newResultId(1, 0, 0), 5, 4)),
                "{ 'group:root', {}, [" +
                        "{ 'grouplist:foo', {}, [{ 'group:2', {}, [{ 'hitlist:hits', {next=3, prev=0}, [hit:2, hit:3] }] }] }, " +
                        "{ 'grouplist:bar', {}, [{ 'group:4', {}, [{ 'hitlist:hits', {prev=2}, [] }] }] }] }");
        assertContinuation(request, result, newComposite(newOffset(newResultId(0, 0, 0), 3, 2),
                newOffset(newResultId(1, 0, 0), 5, 3)),
                "{ 'group:root', {}, [" +
                        "{ 'grouplist:foo', {}, [{ 'group:2', {}, [{ 'hitlist:hits', {prev=0}, [hit:3, hit:4] }] }] }, " +
                        "{ 'grouplist:bar', {}, [{ 'group:4', {}, [{ 'hitlist:hits', {prev=1}, [hit:4] }] }] }] }");
        assertContinuation(request, result, newComposite(newOffset(newResultId(0, 0, 0), 3, 3),
                newOffset(newResultId(1, 0, 0), 5, 2)),
                "{ 'group:root', {}, [" +
                        "{ 'grouplist:foo', {}, [{ 'group:2', {}, [{ 'hitlist:hits', {prev=1}, [hit:4] }] }] }, " +
                        "{ 'grouplist:bar', {}, [{ 'group:4', {}, [{ 'hitlist:hits', {prev=0}, [hit:3, hit:4] }] }] }] }");
        assertContinuation(request, result, newComposite(newOffset(newResultId(0, 0, 0), 3, 4),
                newOffset(newResultId(1, 0, 0), 5, 1)),
                "{ 'group:root', {}, [" +
                        "{ 'grouplist:foo', {}, [{ 'group:2', {}, [{ 'hitlist:hits', {prev=2}, [] }] }] }, " +
                        "{ 'grouplist:bar', {}, [{ 'group:4', {}, [{ 'hitlist:hits', {next=3, prev=0}, [hit:2, hit:3] }] }] }] }");
        assertContinuation(request, result, newComposite(newOffset(newResultId(0, 0, 0), 3, 5),
                newOffset(newResultId(1, 0, 0), 5, 0)),
                "{ 'group:root', {}, [" +
                        "{ 'grouplist:foo', {}, [{ 'group:2', {}, [{ 'hitlist:hits', {prev=2}, [] }] }] }, " +
                        "{ 'grouplist:bar', {}, [{ 'group:4', {}, [{ 'hitlist:hits', {next=2}, [hit:1, hit:2] }] }] }] }");
    }

    @Test
    void requireThatUnstableContinuationsDoNotAffectSiblingHitLists() {
        String request = "all(group(a) each(group(b) max(2) each(max(2) each(output(summary())))))";
        Grouping result = newGrouping(newGroup(2, 201,
                newGroup(3, 301, newHitList(4, 4)),
                newGroup(3, 302, newHitList(4, 4)),
                newGroup(3, 303, newHitList(4, 4)),
                newGroup(3, 304, newHitList(4, 4))),
                newGroup(2, 202,
                        newGroup(3, 305, newHitList(4, 4)),
                        newGroup(3, 306, newHitList(4, 4)),
                        newGroup(3, 307, newHitList(4, 4)),
                        newGroup(3, 308, newHitList(4, 4))));
        assertContinuation(request, result, newComposite(),
                "{ 'group:root', {}, [{ 'grouplist:a', {}, [" +
                        "{ 'group:201', {}, [{ 'grouplist:b', {next=2}, [" +
                        "{ 'group:301', {}, [{ 'hitlist:hits', {next=2}, [hit:1, hit:2] }] }, " +
                        "{ 'group:302', {}, [{ 'hitlist:hits', {next=2}, [hit:1, hit:2] }] }] }] }, " +
                        "{ 'group:202', {}, [{ 'grouplist:b', {next=2}, [" +
                        "{ 'group:305', {}, [{ 'hitlist:hits', {next=2}, [hit:1, hit:2] }] }, " +
                        "{ 'group:306', {}, [{ 'hitlist:hits', {next=2}, [hit:1, hit:2] }] }] }] }] }] }");
        assertContinuation(request, result, newComposite(newOffset(newResultId(0, 1, 0, 1, 0), 4, 2)),
                "{ 'group:root', {}, [{ 'grouplist:a', {}, [" +
                        "{ 'group:201', {}, [{ 'grouplist:b', {next=2}, [" +
                        "{ 'group:301', {}, [{ 'hitlist:hits', {next=2}, [hit:1, hit:2] }] }, " +
                        "{ 'group:302', {}, [{ 'hitlist:hits', {next=2}, [hit:1, hit:2] }] }] }] }, " +
                        "{ 'group:202', {}, [{ 'grouplist:b', {next=2}, [" +
                        "{ 'group:305', {}, [{ 'hitlist:hits', {next=2}, [hit:1, hit:2] }] }, " +
                        "{ 'group:306', {}, [{ 'hitlist:hits', {prev=0}, [hit:3, hit:4] }] }] }] }] }] }");
        assertContinuation(request, result, newComposite(newOffset(newResultId(0, 1, 0, 1, 0), 4, 2),
                newOffset(newResultId(0, 0, 0), 2, 2)),
                "{ 'group:root', {}, [{ 'grouplist:a', {}, [" +
                        "{ 'group:201', {}, [{ 'grouplist:b', {prev=0}, [" +
                        "{ 'group:303', {}, [{ 'hitlist:hits', {next=2}, [hit:1, hit:2] }] }, " +
                        "{ 'group:304', {}, [{ 'hitlist:hits', {next=2}, [hit:1, hit:2] }] }] }] }, " +
                        "{ 'group:202', {}, [{ 'grouplist:b', {next=2}, [" +
                        "{ 'group:305', {}, [{ 'hitlist:hits', {next=2}, [hit:1, hit:2] }] }, " +
                        "{ 'group:306', {}, [{ 'hitlist:hits', {prev=0}, [hit:3, hit:4] }] }] }] }] }] }");
        assertContinuation(request, result, newComposite(newOffset(newResultId(0, 1, 0, 1, 0), 4, 2),
                newOffset(newResultId(0, 0, 0), 2, 2),
                newUnstableOffset(newResultId(0, 1, 0), 2, 1)),
                "{ 'group:root', {}, [{ 'grouplist:a', {}, [" +
                        "{ 'group:201', {}, [{ 'grouplist:b', {prev=0}, [" +
                        "{ 'group:303', {}, [{ 'hitlist:hits', {next=2}, [hit:1, hit:2] }] }, " +
                        "{ 'group:304', {}, [{ 'hitlist:hits', {next=2}, [hit:1, hit:2] }] }] }] }, " +
                        "{ 'group:202', {}, [{ 'grouplist:b', {next=3, prev=0}, [" +
                        "{ 'group:306', {}, [{ 'hitlist:hits', {next=2}, [hit:1, hit:2] }] }, " +
                        "{ 'group:307', {}, [{ 'hitlist:hits', {next=2}, [hit:1, hit:2] }] }] }] }] }] }");
    }

    // --------------------------------------------------------------------------------
    //
    // Utilities.
    //
    // --------------------------------------------------------------------------------

    private static CompositeContinuation newComposite(EncodableContinuation... conts) {
        CompositeContinuation ret = new CompositeContinuation();
        for (EncodableContinuation cont : conts) {
            ret.add(cont);
        }
        return ret;
    }

    private static ResultId newResultId(int... indexes) {
        ResultId id = ResultId.valueOf(REQUEST_ID).newChildId(ROOT_IDX);
        for (int i : indexes) {
            id = id.newChildId(i);
        }
        return id;
    }

    private static OffsetContinuation newOffset(ResultId resultId, int tag, int offset) {
        return new OffsetContinuation(resultId, tag, offset, 0);
    }

    private static OffsetContinuation newUnstableOffset(ResultId resultId, int tag, int offset) {
        return new OffsetContinuation(resultId, tag, offset, OffsetContinuation.FLAG_UNSTABLE);
    }

    private static Grouping newGrouping(Group... children) {
        Group root = new Group();
        root.setTag(1);
        for (Group child : children) {
            root.addChild(child);
        }
        Grouping grouping = new Grouping();
        grouping.setRoot(root);
        return grouping;
    }

    private static Group newGroup(int tag, AggregationResult... results) {
        return newGroup(tag, tag > 1 ? tag : 0, results);
    }

    private static Group newGroup(int tag, int id, AggregationResult... results) {
        Group group = new Group();
        group.setTag(tag);
        if (id > 0) {
            group.setId(new IntegerResultNode(id));
        }
        for (AggregationResult result : results) {
            group.addAggregationResult(result);
        }
        return group;
    }

    private static Group newGroup(int tag, int id, Group child0, Group... childN) {
        Group group = new Group();
        group.setTag(tag);
        if (id > 0) {
            group.setId(new IntegerResultNode(id));
        }
        group.addChild(child0);
        for (Group child : childN) {
            group.addChild(child);
        }
        return group;
    }

    private static HitsAggregationResult newHitList(int hitsTag, int numHits) {
        HitsAggregationResult res = new HitsAggregationResult();
        res.setTag(hitsTag);
        res.setSummaryClass("default");
        for (int i = 0; i < numHits; ++i) {
            res.addHit(new FS4Hit(i + 1, new GlobalId(IdString.createIdString("id:ns:type::")), 1));
        }
        return res;
    }

    private static void assertGroupId(String expected, ResultNode actual) {
        assertLayout("all(group(a) each(output(count())))",
                     newGrouping(new Group().setTag(2).setId(actual)),
                     "RootGroup{id=group:root}[GroupList{label=a}[Group{id=" + expected + "}[]]]");
    }

    private static void assertResult(String expected, AggregationResult actual) {
        actual.setTag(3);
        assertLayout("all(group(a) each(output(count())))",
                     newGrouping(newGroup(2, 2, actual)),
                     "RootGroup{id=group:root}[GroupList{label=a}[Group{id=group:2, count()=" + expected + "}[]]]");
    }

    private static void assertBuild(String request, List<Grouping> result) {
        ResultTest test = new ResultTest();
        test.result.addAll(result);
        test.request = request;
        assertOutput(test);
    }

    private static void assertBuildFail(String request, List<Grouping> result, String expected) {
        ResultTest test = new ResultTest();
        test.result.addAll(result);
        test.request = request;
        test.expectedException = expected;
        assertOutput(test);
    }

    private static void assertResultCont(String request, Grouping result, Continuation cont, String expected) {
        assertOutput(request, Arrays.asList(result), cont, new ResultContWriter(), expected);
    }

    private static void assertResultCont(String request, List<Grouping> result, String expected) {
        assertOutput(request, result, null, new ResultContWriter(), expected);
    }

    private static void assertResultCont(String request, List<Grouping> result, Continuation cont, String expected) {
        assertOutput(request, result, cont, new ResultContWriter(), expected);
    }

    private static void assertContinuation(String request, Grouping result, String expected) {
        assertOutput(request, Arrays.asList(result), null, new ContinuationWriter(), expected);
    }

    private static void assertContinuation(String request, Grouping result, Continuation cont, String expected) {
        assertOutput(request, Arrays.asList(result), cont, new ContinuationWriter(), expected);
    }

    private static void assertContinuation(String request, List<Grouping> result, Continuation cont, String expected) {
        assertOutput(request, result, cont, new ContinuationWriter(), expected);
    }

    private static void assertLayout(String request, Grouping result, String expected) {
        assertOutput(request, Arrays.asList(result), null, new LayoutWriter(), expected);
    }

    private static void assertLayout(String request, List<Grouping> result, String expected) {
        assertOutput(request, result, null, new LayoutWriter(), expected);
    }

    private static void assertOutput(String request, List<Grouping> result, Continuation continuation,
                                     OutputWriter writer, String expected) {
        ResultTest test = new ResultTest();
        test.result.addAll(result);
        test.request = request;
        test.outputWriter = writer;
        test.continuation = continuation;
        test.expectedOutput = expected;
        assertOutput(test);
    }

    private static void assertOutput(ResultTest test) {
        RequestBuilder reqBuilder = new RequestBuilder(REQUEST_ID);
        reqBuilder.setRootOperation(GroupingOperation.fromString(test.request));
        reqBuilder.addContinuations(Arrays.asList(test.continuation));
        reqBuilder.build();
        assertEquals(reqBuilder.getRequestList().size(), test.result.size());

        ResultBuilder resBuilder = new ResultBuilder();
        resBuilder.setHitConverter(new MyHitConverter());
        resBuilder.setTransform(reqBuilder.getTransform());
        resBuilder.setRequestId(REQUEST_ID);
        for (int i = 0, len = test.result.size(); i < len; ++i) {
            Grouping grouping = test.result.get(i);
            grouping.setId(i);
            resBuilder.addGroupingResult(grouping);
        }
        try {
            resBuilder.build();
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
            String output = test.outputWriter.write(resBuilder);
            assertEquals(test.expectedOutput, output);
        }
    }

    private static String getCanonicalId(com.yahoo.search.result.Hit hit) {
        String str = hit.getId().toString();
        if (!str.startsWith("group:")) {
            return str;
        }
        if (str.startsWith("group:root:")) {
            return "group:root";
        }
        int pos = str.indexOf(':', 6);
        if (pos < 0) {
            return str;
        }
        return "group:" + str.substring(pos + 1);
    }

    private static class ResultTest {

        List<Grouping> result = new LinkedList<>();
        String request;
        String expectedOutput;
        String expectedException;
        OutputWriter outputWriter;
        Continuation continuation;
    }

    private static interface OutputWriter {

        String write(ResultBuilder builder);
    }

    private static class ResultContWriter implements OutputWriter {

        @Override
        public String write(ResultBuilder builder) {
            return toString(builder.getContinuation());
        }

        String toString(Continuation cnt) {
            if (cnt instanceof OffsetContinuation) {
                OffsetContinuation off = (OffsetContinuation)cnt;
                String id = off.getResultId().toString().replace(", ", ".");
                return id.substring(5, id.length() - 1) + "=" + off.getOffset();
            } else if (cnt instanceof CompositeContinuation) {
                List<String> children = new LinkedList<>();
                for (Continuation child : (CompositeContinuation)cnt) {
                    children.add(toString(child));
                }
                return children.toString();
            } else {
                throw new UnsupportedOperationException(cnt.getClass().getName());
            }
        }
    }

    private static class ContinuationWriter implements OutputWriter {

        @Override
        public String write(ResultBuilder builder) {
            return toString(builder.getRoot());
        }

        String toString(com.yahoo.search.result.Hit hit) {
            Map<String, String> conts = new TreeMap<>();
            if (hit instanceof AbstractList) {
                for (Map.Entry<String, Continuation> entry : ((AbstractList)hit).continuations().entrySet()) {
                    conts.put(entry.getKey(), toString(entry.getValue()));
                }
            }
            List<String> children = new LinkedList<>();
            if (hit instanceof HitGroup) {
                for (com.yahoo.search.result.Hit childHit : (HitGroup)hit) {
                    if (childHit instanceof HitGroup) {
                        children.add(toString(childHit));
                    } else {
                        children.add(childHit.getId().toString());
                    }
                }
            }
            return "{ '" + getCanonicalId(hit) + "', " + conts + ", " + children + " }";
        }

        String toString(Continuation cnt) {
            if (cnt instanceof OffsetContinuation) {
                return String.valueOf(((OffsetContinuation)cnt).getOffset());
            } else if (cnt instanceof CompositeContinuation) {
                List<String> children = new LinkedList<>();
                for (Continuation child : (CompositeContinuation)cnt) {
                    children.add(toString(child));
                }
                Collections.sort(children);
                return children.toString();
            } else {
                throw new UnsupportedOperationException(cnt.getClass().getName());
            }
        }
    }

    private static class LayoutWriter implements OutputWriter {

        @Override
        public String write(ResultBuilder builder) {
            return toString(builder.getRoot());
        }

        String toString(com.yahoo.search.result.Hit hit) {
            StringBuilder ret = new StringBuilder();
            ret.append(hit.getClass().getSimpleName());

            Map<String, String> members = new LinkedHashMap<>();
            if (hit instanceof GroupList) {
                members.put("label", ((GroupList)hit).getLabel());
            } else if (hit instanceof HitList) {
                members.put("label", ((HitList)hit).getLabel());
            } else {
                members.put("id", getCanonicalId(hit));
            }
            for (Map.Entry<String, Object> entry : hit.fields().entrySet()) {
                members.put(entry.getKey(), String.valueOf(entry.getValue()));
            }
            ret.append(members);

            if (hit instanceof HitGroup) {
                List<String> children = new LinkedList<>();
                for (com.yahoo.search.result.Hit childHit : (HitGroup)hit) {
                    children.add(toString(childHit));
                }
                ret.append(children);
            }
            return ret.toString();
        }
    }

    private static class MyHitConverter implements ResultBuilder.HitConverter {

        @Override
        public com.yahoo.search.result.Hit toSearchHit(String summaryClass, com.yahoo.searchlib.aggregation.Hit hit) {
            return new com.yahoo.search.result.Hit("hit:" + ((FS4Hit)hit).getPath(), new Relevance(0));
        }
    }

    private static class MyAggregationResult extends AggregationResult {

        @Override
        public ResultNode getRank() {
            return null;
        }

        @Override
        protected void onMerge(AggregationResult result) {

        }

        @Override
        protected boolean equalsAggregation(AggregationResult obj) {
            return false;
        }
    }

    private static class MyResultNode extends ResultNode {

        @Override
        protected void set(ResultNode rhs) {

        }

        @Override
        protected int onCmp(ResultNode rhs) {
            return 0;
        }

        @Override
        public long getInteger() {
            return 0;
        }

        @Override
        public double getFloat() {
            return 0;
        }

        @Override
        public String getString() {
            return null;
        }

        @Override
        public byte[] getRaw() {
            return new byte[0];
        }
    }
}
