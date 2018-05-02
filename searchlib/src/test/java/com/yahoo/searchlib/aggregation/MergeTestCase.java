// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.aggregation;

import com.yahoo.document.DocumentId;
import com.yahoo.document.GlobalId;
import com.yahoo.searchlib.expression.AggregationRefNode;
import com.yahoo.searchlib.expression.AttributeNode;
import com.yahoo.searchlib.expression.ConstantNode;
import com.yahoo.searchlib.expression.FloatBucketResultNode;
import com.yahoo.searchlib.expression.FloatResultNode;
import com.yahoo.searchlib.expression.IntegerResultNode;
import com.yahoo.searchlib.expression.MultiplyFunctionNode;
import com.yahoo.searchlib.expression.StringResultNode;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * @author Simon Thoresen
 */
public class MergeTestCase {

    private GlobalId createGlobalId(int docId) {
        return new GlobalId((new DocumentId("doc:test:" + docId)).getGlobalId());
    }

    // Test merging of hits.
    @Test
    public void testMergeHits() {
        Grouping request = new Grouping()
            .setFirstLevel(0)
            .setLastLevel(1)
            .addLevel(new GroupingLevel().setMaxGroups(69));

        Group expect = new Group()
            .addAggregationResult(new HitsAggregationResult()
                       .setMaxHits(5)
                       .addHit(new FS4Hit(30, createGlobalId(30), 30))
                       .addHit(new FS4Hit(20, createGlobalId(20), 20))
                       .addHit(new FS4Hit(10, createGlobalId(10), 10))
                       .addHit(new FS4Hit(5, createGlobalId(9), 9))
                       .addHit(new FS4Hit(6, createGlobalId(8), 8))
                       .setExpression(new ConstantNode(new IntegerResultNode(0))));

        Group a = new Group()
            .addAggregationResult(new HitsAggregationResult()
                       .setMaxHits(5)
                       .addHit(new FS4Hit(10, createGlobalId(10), 10))
                       .addHit(new FS4Hit(1, createGlobalId(5), 5))
                       .addHit(new FS4Hit(2, createGlobalId(4), 4))
                       .setExpression(new ConstantNode( new IntegerResultNode(0))));

        Group b = new Group()
            .addAggregationResult(new HitsAggregationResult()
                       .setMaxHits(5)
                       .addHit(new FS4Hit(20, createGlobalId(20), 20))
                       .addHit(new FS4Hit(3, createGlobalId(7), 7))
                       .addHit(new FS4Hit(4, createGlobalId(6), 6))
                       .setExpression(new ConstantNode( new IntegerResultNode(0))));

        Group c = new Group()
            .addAggregationResult(new HitsAggregationResult()
                       .setMaxHits(5)
                       .addHit(new FS4Hit(30, createGlobalId(30), 30))
                       .addHit(new FS4Hit(5, createGlobalId(9), 9))
                       .addHit(new FS4Hit(6, createGlobalId(8), 8))
                       .setExpression(new ConstantNode( new IntegerResultNode(0))));

        assertMerge(request, a, b, c, expect);
        assertMerge(request, a, c, b, expect);
        assertMerge(request, b, a, c, expect);
        assertMerge(request, c, a, b, expect);
        assertMerge(request, b, c, a, expect);
        assertMerge(request, c, b, a, expect);
    }

    // Test merging the sum of the values from a single attribute vector that was collected directly into the root node.
    @Test
    public void testMergeSimpleSum() {
        Grouping lhs = new Grouping()
            .setRoot(new Group()
                     .addAggregationResult(new SumAggregationResult()
                                .setSum(new IntegerResultNode(20))
                                .setExpression(new AttributeNode("foo"))));

        Grouping rhs = new Grouping()
            .setRoot(new Group()
                     .addAggregationResult(new SumAggregationResult()
                                .setSum(new IntegerResultNode(30))
                                .setExpression(new AttributeNode("foo"))));

        Group expect = new Group()
            .addAggregationResult(new SumAggregationResult()
                       .setSum(new IntegerResultNode(50))
                       .setExpression(new AttributeNode("foo")));

        assertMerge(lhs, rhs, expect);
    }

    // Test merging of the value from a single attribute vector in level 1.
    @Test
    public void testMergeSingleChild() {
        Grouping lhs = new Grouping()
            .setFirstLevel(0)
            .setLastLevel(1)
            .setRoot(new Group().addChild(new Group()
                                          .setId(new StringResultNode("foo"))
                                          .addAggregationResult(new SumAggregationResult()
                                                     .setSum(new IntegerResultNode(20))
                                                     .setExpression(new AttributeNode("foo")))));

        Grouping rhs = new Grouping()
            .setFirstLevel(0)
            .setLastLevel(1)
            .setRoot(new Group().addChild(new Group()
                                          .setId(new StringResultNode("foo"))
                                          .addAggregationResult(new SumAggregationResult()
                                                     .setSum(new IntegerResultNode(30))
                                                     .setExpression(new AttributeNode("foo")))));

        Group expect = new Group().addChild(new Group()
                                            .setId(new StringResultNode("foo"))
                                            .addAggregationResult(new SumAggregationResult()
                                                       .setSum(new IntegerResultNode(50))
                                                       .setExpression(new AttributeNode("foo"))));

        assertMerge(lhs, rhs, expect);
    }

    // Test merging of the value from a multiple attribute vectors in level 1.
    @Test
    public void testMergeMultiChild() {
        Grouping lhs = new Grouping()
            .setFirstLevel(0)
            .setLastLevel(1)
            .setRoot(new Group()
                     .addChild(new Group()
                               .setId(new StringResultNode("foo"))
                               .addAggregationResult(new SumAggregationResult()
                                          .setSum(new IntegerResultNode(20))
                                          .setExpression(new AttributeNode("foo"))))
                     .addChild(new Group()
                               .setId(new StringResultNode("bar"))
                               .addAggregationResult(new SumAggregationResult()
                                          .setSum(new IntegerResultNode(40))
                                          .setExpression(new AttributeNode("foo")))));

        Grouping rhs = new Grouping()
            .setFirstLevel(0)
            .setLastLevel(1)
            .setRoot(new Group()
                     .addChild(new Group()
                               .setId(new StringResultNode("foo"))
                               .addAggregationResult(new SumAggregationResult()
                                          .setSum(new IntegerResultNode(30))
                                          .setExpression(new AttributeNode("foo"))))
                     .addChild(new Group()
                               .setId(new StringResultNode("baz"))
                               .addAggregationResult(new SumAggregationResult()
                                          .setSum(new IntegerResultNode(30))
                                          .setExpression(new AttributeNode("foo")))));

        Group expect = new Group().addChild(
            new Group()
            .setId(new StringResultNode("foo"))
            .addAggregationResult(new SumAggregationResult()
                       .setSum(new IntegerResultNode(50))
                       .setExpression(new AttributeNode("foo"))))
            .addChild(new Group()
                      .setId(new StringResultNode("bar"))
                      .addAggregationResult(new SumAggregationResult()
                                 .setSum(new IntegerResultNode(40))
                                 .setExpression(new AttributeNode("foo"))))
            .addChild(new Group()
                      .setId(new StringResultNode("baz"))
                      .addAggregationResult(new SumAggregationResult()
                                 .setSum(new IntegerResultNode(30))
                                 .setExpression(new AttributeNode("foo"))));

        assertMerge(lhs, rhs, expect);
    }

    // Verify that frozen levels are not touched during merge.
    @Test
    public void testMergeLevels() {
        Grouping request = new Grouping()
            .addLevel(new GroupingLevel()
                      .setExpression(new AttributeNode("c1"))
                      .setGroupPrototype(new Group().addAggregationResult(
                              new SumAggregationResult().setExpression(new AttributeNode("s1")))))
            .addLevel(new GroupingLevel()
                      .setExpression(new AttributeNode("c2"))
                      .setGroupPrototype(new Group().addAggregationResult(
                              new SumAggregationResult().setExpression(new AttributeNode("s2")))))
            .addLevel(new GroupingLevel()
                      .setExpression(new AttributeNode("c3"))
                      .setGroupPrototype(new Group().addAggregationResult(
                              new SumAggregationResult().setExpression(new AttributeNode("s3")))));

        Group lhs = new Group()
            .addAggregationResult(new SumAggregationResult()
                       .setSum(new IntegerResultNode(5))
                       .setExpression(new AttributeNode("s0")))
            .addChild(new Group()
                      .setId(new IntegerResultNode(10))
                      .addAggregationResult(new SumAggregationResult()
                                 .setSum(new IntegerResultNode(10))
                                 .setExpression(new AttributeNode("s1")))
                      .addChild(new Group()
                                .setId(new IntegerResultNode(20))
                                .addAggregationResult(new SumAggregationResult()
                                           .setSum(new IntegerResultNode(15))
                                           .setExpression(new AttributeNode("s2")))
                                .addChild(new Group()
                                          .setId(new IntegerResultNode(30))
                                          .addAggregationResult(new SumAggregationResult()
                                                     .setSum(new IntegerResultNode(20))
                                                     .setExpression(new AttributeNode("s3"))))));

        Group rhs = new Group()
            .addAggregationResult(new SumAggregationResult()
                       .setSum(new IntegerResultNode(5))
                       .setExpression(new AttributeNode("s0")))
            .addChild(new Group()
                      .setId(new IntegerResultNode(10))
                      .addAggregationResult(new SumAggregationResult()
                                 .setSum(new IntegerResultNode(10))
                                 .setExpression(new AttributeNode("s1")))
                      .addChild(new Group()
                                .setId(new IntegerResultNode(20))
                                .addAggregationResult(new SumAggregationResult()
                                           .setSum(new IntegerResultNode(15))
                                           .setExpression(new AttributeNode("s2")))
                                .addChild(new Group()
                                          .setId(new IntegerResultNode(30))
                                          .addAggregationResult(new SumAggregationResult()
                                                     .setSum(new IntegerResultNode(20))
                                                     .setExpression(new AttributeNode("s3"))))));

        Group expectAll = new Group()
            .addAggregationResult(new SumAggregationResult()
                       .setSum(new IntegerResultNode(10))
                       .setExpression(new AttributeNode("s0")))
            .addChild(new Group()
                      .setId(new IntegerResultNode(10))
                      .addAggregationResult(new SumAggregationResult()
                                 .setSum(new IntegerResultNode(20))
                                 .setExpression(new AttributeNode("s1")))
                      .addChild(new Group()
                                .setId(new IntegerResultNode(20))
                                .addAggregationResult(new SumAggregationResult()
                                           .setSum(new IntegerResultNode(30))
                                           .setExpression(new AttributeNode("s2")))
                                .addChild(new Group()
                                          .setId(new IntegerResultNode(30))
                                          .addAggregationResult(new SumAggregationResult()
                                                     .setSum(new IntegerResultNode(40))
                                                     .setExpression(new AttributeNode("s3"))))));

        Group expect0 = new Group()
            .addAggregationResult(new SumAggregationResult()
                       .setSum(new IntegerResultNode(5))
                       .setExpression(new AttributeNode("s0")))
            .addChild(new Group()
                      .setId(new IntegerResultNode(10))
                      .addAggregationResult(new SumAggregationResult()
                                 .setSum(new IntegerResultNode(20))
                                 .setExpression(new AttributeNode("s1")))
                      .addChild(new Group()
                                .setId(new IntegerResultNode(20))
                                .addAggregationResult(new SumAggregationResult()
                                           .setSum(new IntegerResultNode(30))
                                           .setExpression(new AttributeNode("s2")))
                                .addChild(new Group()
                                          .setId(new IntegerResultNode(30))
                                          .addAggregationResult(new SumAggregationResult()
                                                     .setSum(new IntegerResultNode(40))
                                                     .setExpression(new AttributeNode("s3"))))));

        Group expect1 = new Group()
            .addAggregationResult(new SumAggregationResult()
                       .setSum(new IntegerResultNode(5))
                       .setExpression(new AttributeNode("s0")))
            .addChild(new Group()
                      .setId(new IntegerResultNode(10))
                      .addAggregationResult(new SumAggregationResult()
                                 .setSum(new IntegerResultNode(10))
                                 .setExpression(new AttributeNode("s1")))
                      .addChild(new Group()
                                .setId(new IntegerResultNode(20))
                                .addAggregationResult(new SumAggregationResult()
                                           .setSum(new IntegerResultNode(30))
                                           .setExpression(new AttributeNode("s2")))
                                .addChild(new Group()
                                          .setId(new IntegerResultNode(30))
                                          .addAggregationResult(new SumAggregationResult()
                                                     .setSum(new IntegerResultNode(40))
                                                     .setExpression(new AttributeNode("s3"))))));

        Group expect2 = new Group()
            .addAggregationResult(new SumAggregationResult()
                       .setSum(new IntegerResultNode(5))
                       .setExpression(new AttributeNode("s0")))
            .addChild(new Group()
                      .setId(new IntegerResultNode(10))
                      .addAggregationResult(new SumAggregationResult()
                                 .setSum(new IntegerResultNode(10))
                                 .setExpression(new AttributeNode("s1")))
                      .addChild(new Group()
                                .setId(new IntegerResultNode(20))
                                .addAggregationResult(new SumAggregationResult()
                                           .setSum(new IntegerResultNode(15))
                                           .setExpression(new AttributeNode("s2")))
                                .addChild(new Group()
                                          .setId(new IntegerResultNode(30))
                                          .addAggregationResult(new SumAggregationResult()
                                                     .setSum(new IntegerResultNode(40))
                                                     .setExpression(new AttributeNode("s3"))))));

        Group expect3 = new Group()
            .addAggregationResult(new SumAggregationResult()
                       .setSum(new IntegerResultNode(5))
                       .setExpression(new AttributeNode("s0")))
            .addChild(new Group()
                      .setId(new IntegerResultNode(10))
                      .addAggregationResult(new SumAggregationResult()
                                 .setSum(new IntegerResultNode(10))
                                 .setExpression(new AttributeNode("s1")))
                      .addChild(new Group()
                                .setId(new IntegerResultNode(20))
                                .addAggregationResult(new SumAggregationResult()
                                           .setSum(new IntegerResultNode(15))
                                           .setExpression(new AttributeNode("s2")))
                                .addChild(new Group()
                                          .setId(new IntegerResultNode(30))
                                          .addAggregationResult(new SumAggregationResult()
                                                     .setSum(new IntegerResultNode(20))
                                                     .setExpression(new AttributeNode("s3"))))));

        request.setFirstLevel(0).setLastLevel(3);
        assertMerge(request, lhs, rhs, expectAll);
        request.setFirstLevel(1).setLastLevel(3);
        assertMerge(request, lhs, rhs, expect0);
        request.setFirstLevel(2).setLastLevel(5);
        assertMerge(request, lhs, rhs, expect1);
        request.setFirstLevel(3).setLastLevel(5);
        assertMerge(request, lhs, rhs, expect2);
        request.setFirstLevel(4).setLastLevel(4);
        assertMerge(request, lhs, rhs, expect3);
    }

    // Verify that the number of groups for a level is pruned down to maxGroups, that the remaining groups are the
    // highest ranked ones, and that they are sorted by group id.
    @Test
    public void testMergeGroups() {
        Grouping request = new Grouping()
            .addLevel(new GroupingLevel()
                      .setExpression(new AttributeNode("attr")));
        Group lhs = new Group()
            .addChild(new Group().setId(new IntegerResultNode(5)).setRank(5))
            .addChild(new Group().setId(new IntegerResultNode(10)).setRank(5))
            .addChild(new Group().setId(new IntegerResultNode(15)).setRank(15))
            .addChild(new Group().setId(new IntegerResultNode(40)).setRank(100))
            .addChild(new Group().setId(new IntegerResultNode(50)).setRank(30));

        Group rhs = new Group()
            .addChild(new Group().setId(new IntegerResultNode(0)).setRank(10))
            .addChild(new Group().setId(new IntegerResultNode(10)).setRank(50))
            .addChild(new Group().setId(new IntegerResultNode(20)).setRank(25))
            .addChild(new Group().setId(new IntegerResultNode(40)).setRank(10))
            .addChild(new Group().setId(new IntegerResultNode(45)).setRank(20));

        Group expect3 = new Group()
            .addChild(new Group().setId(new IntegerResultNode(10)).setRank(50))
            .addChild(new Group().setId(new IntegerResultNode(40)).setRank(100))
            .addChild(new Group().setId(new IntegerResultNode(50)).setRank(30));

        Group expect5 = new Group()
            .addChild(new Group().setId(new IntegerResultNode(10)).setRank(50))
            .addChild(new Group().setId(new IntegerResultNode(20)).setRank(25))
            .addChild(new Group().setId(new IntegerResultNode(40)).setRank(100))
            .addChild(new Group().setId(new IntegerResultNode(45)).setRank(20))
            .addChild(new Group().setId(new IntegerResultNode(50)).setRank(30));

        Group expectAll = new Group()
            .addChild(new Group().setId(new IntegerResultNode(0)).setRank(10))
            .addChild(new Group().setId(new IntegerResultNode(5)).setRank(5))
            .addChild(new Group().setId(new IntegerResultNode(10)).setRank(50))
            .addChild(new Group().setId(new IntegerResultNode(15)).setRank(15))
            .addChild(new Group().setId(new IntegerResultNode(20)).setRank(25))
            .addChild(new Group().setId(new IntegerResultNode(40)).setRank(100))
            .addChild(new Group().setId(new IntegerResultNode(45)).setRank(20))
            .addChild(new Group().setId(new IntegerResultNode(50)).setRank(30));

        request.getLevels().get(0).setMaxGroups(3);
        assertMerge(request, lhs, rhs, expect3);
        assertMerge(request, rhs, lhs, expect3);

        request.getLevels().get(0).setMaxGroups(5);
        assertMerge(request, lhs, rhs, expect5);
        assertMerge(request, rhs, lhs, expect5);

        request.getLevels().get(0).setMaxGroups(-1);
        assertMerge(request, lhs, rhs, expectAll);
        assertMerge(request, rhs, lhs, expectAll);
    }

    @Test
    public void testMergeBuckets() {
          Grouping lhs = new Grouping()
                .setRoot(new Group().setTag(0)
                                    .addChild(new Group().setId(new FloatBucketResultNode(FloatResultNode.getNegativeInfinity().getFloat(), 0.4))
                                                         .addAggregationResult(new CountAggregationResult().setCount(1))
                                                         .setTag(1))
                                    .addChild(new Group().setId(new FloatBucketResultNode(0, 0))
                                                         .addAggregationResult(new CountAggregationResult().setCount(12))
                                                         .setTag(1)));

          Grouping rhs = new Grouping()
                .setRoot(new Group().setTag(0)
                                    .addChild(new Group().setId(new FloatBucketResultNode(FloatResultNode.getNegativeInfinity().getFloat(), 0.4))
                                                         .addAggregationResult(new CountAggregationResult().setCount(0))
                                                         .setTag(1))
                                    .addChild(new Group().setId(new FloatBucketResultNode(0, 0))
                                                         .addAggregationResult(new CountAggregationResult().setCount(15))
                                                         .setTag(1)));

          Group expected = new Group().setTag(0)
                                    .addChild(new Group().setId(new FloatBucketResultNode(FloatResultNode.getNegativeInfinity().getFloat(), 0.4))
                                                         .addAggregationResult(new CountAggregationResult().setCount(1))
                                                         .setTag(1))
                                    .addChild(new Group().setId(new FloatBucketResultNode(0, 0))
                                                         .addAggregationResult(new CountAggregationResult().setCount(27))
                                                         .setTag(1));
          assertMerge(lhs, rhs, expected);
    }

    // Merge two trees that are ordered by an expression, and verify that the resulting order after merge is correct.
    @Test
    public void testMergeExpressions() {
        Grouping a = new Grouping()
                .setFirstLevel(0)
                .setLastLevel(1)
                .addLevel(new GroupingLevel().setMaxGroups(1))
                .setRoot(new Group()
                         .addChild(new Group().setId(new StringResultNode("aa"))
                                       .addAggregationResult(new MaxAggregationResult().setMax(new IntegerResultNode(9)))
                                       .addAggregationResult(new CountAggregationResult().setCount(2))
                                       .addOrderBy(new MultiplyFunctionNode().addArg(new AggregationRefNode(0))
                                                                             .addArg(new AggregationRefNode(1)), true)));
        Grouping b = new Grouping()
                .setFirstLevel(0)
                .setLastLevel(1)
                .addLevel(new GroupingLevel().setMaxGroups(1))
                .setRoot(new Group()
                         .addChild(new Group().setId(new StringResultNode("ab"))
                                       .addAggregationResult(new MaxAggregationResult().setMax(
                                               new IntegerResultNode(12)))
                                       .addAggregationResult(new CountAggregationResult().setCount(1))
                                       .addOrderBy(new MultiplyFunctionNode().addArg(new AggregationRefNode(0))
                                                                             .addArg(new AggregationRefNode(1)), true)));

        Grouping expected = new Grouping()
                .setFirstLevel(0)
                .setLastLevel(1)
                .addLevel(new GroupingLevel().setMaxGroups(1))
                .setRoot(new Group()
                         .addChild(new Group().setId(new StringResultNode("ab"))
                                       .addAggregationResult(new MaxAggregationResult().setMax(
                                               new IntegerResultNode(12)))
                                       .addAggregationResult(new CountAggregationResult().setCount(1))
                                       .addOrderBy(new MultiplyFunctionNode().addArg(new AggregationRefNode(0))
                                                                             .addArg(new AggregationRefNode(1)), true)));
        expected.postMerge();

        a.merge(b);
        a.postMerge();
        assertEquals(expected.toString(), a.toString());
    }

    // Merge two relatively complex tree structures and verify that the end result is as expected.
    @Test
    public void testMergeTrees() {
        Grouping request = new Grouping()
            .addLevel(new GroupingLevel()
                      .setMaxGroups(3)
                      .setExpression(new AttributeNode("c1"))
                      .setGroupPrototype(new Group().addAggregationResult(
                              new SumAggregationResult().setExpression(new AttributeNode("s1")))))
            .addLevel(new GroupingLevel()
                              .setMaxGroups(2)
                              .setExpression(new AttributeNode("c2"))
                              .setGroupPrototype(new Group().addAggregationResult(
                                      new SumAggregationResult().setExpression(new AttributeNode("s2")))))
            .addLevel(new GroupingLevel()
                              .setMaxGroups(1)
                              .setExpression(new AttributeNode("c3"))
                              .setGroupPrototype(new Group().addAggregationResult(
                                      new SumAggregationResult().setExpression(new AttributeNode("s3")))));

        Group lhs = new Group()
            .addAggregationResult(new SumAggregationResult()
                       .setSum(new IntegerResultNode(100))
                       .setExpression(new AttributeNode("s0")))
            .addChild(new Group().setId(new IntegerResultNode(4)).setRank(10))
            .addChild(new Group()
                      .setId(new IntegerResultNode(5))
                      .setRank(5) // merged with 200 rank node
                      .addAggregationResult(new SumAggregationResult()
                                 .setSum(new IntegerResultNode(100))
                                 .setExpression(new AttributeNode("s1")))
                      .addChild(new Group().setId(new IntegerResultNode(4)).setRank(10))
                      .addChild(new Group()
                                .setId(new IntegerResultNode(5))
                                .setRank(500)
                                .addAggregationResult(new SumAggregationResult()
                                           .setSum(new IntegerResultNode(100))
                                           .setExpression(new AttributeNode("s2")))
                                .addChild(new Group().setId(new IntegerResultNode(4)).setRank(10))
                                .addChild(new Group()
                                          .setId(new IntegerResultNode(5))
                                          .setRank(200)
                                          .addAggregationResult(new SumAggregationResult()
                                                     .setSum(new IntegerResultNode(100))
                                                     .setExpression(new AttributeNode("s3"))))))
            .addChild(new Group().setId(new IntegerResultNode(9)).setRank(10))
            .addChild(new Group()
                      .setId(new IntegerResultNode(10))
                      .setRank(100)
                      .addAggregationResult(new SumAggregationResult()
                                 .setSum(new IntegerResultNode(100))
                                 .setExpression(new AttributeNode("s1")))
                      // dummy child would be picked up here
                      .addChild(new Group()
                                .setId(new IntegerResultNode(15))
                                .setRank(200)
                                .addAggregationResult(new SumAggregationResult()
                                           .setSum(new IntegerResultNode(100))
                                           .setExpression(new AttributeNode("s2")))
                                .addChild(new Group().setId(new IntegerResultNode(14)).setRank(10))
                                .addChild(new Group()
                                          .setId(new IntegerResultNode(15))
                                          .setRank(300)
                                          .addAggregationResult(new SumAggregationResult()
                                                     .setSum(new IntegerResultNode(100))
                                                     .setExpression(new AttributeNode("s3"))))))
            .addChild(new Group().setId(new IntegerResultNode(14)).setRank(10))
            .addChild(new Group()
                      .setId(new IntegerResultNode(15))
                      .setRank(300)
                      .addAggregationResult(new SumAggregationResult()
                                 .setSum(new IntegerResultNode(100))
                                 .setExpression(new AttributeNode("s1")))
                      .addChild(new Group().setId(new IntegerResultNode(19)).setRank(10))
                      .addChild(new Group()
                                .setId(new IntegerResultNode(20))
                                .setRank(100)
                                .addAggregationResult(new SumAggregationResult()
                                           .setSum(new IntegerResultNode(100))
                                           .setExpression(new AttributeNode("s2")))));

        Group rhs = new Group()
            .addAggregationResult(new SumAggregationResult()
                       .setSum(new IntegerResultNode(100))
                       .setExpression(new AttributeNode("s0")))
            .addChild(new Group().setId(new IntegerResultNode(4)).setRank(10))
            .addChild(new Group()
                      .setId(new IntegerResultNode(5))
                      .setRank(200)
                      .addAggregationResult(new SumAggregationResult()
                                 .setSum(new IntegerResultNode(100))
                                 .setExpression(new AttributeNode("s1")))
                      .addChild(new Group().setId(new IntegerResultNode(9)).setRank(10))
                      .addChild(new Group()
                                .setId(new IntegerResultNode(10))
                                .setRank(400)
                                .addAggregationResult(new SumAggregationResult()
                                           .setSum(new IntegerResultNode(100))
                                           .setExpression(new AttributeNode("s2")))
                                .addChild(new Group().setId(new IntegerResultNode(9)).setRank(10))
                                .addChild(new Group()
                                          .setId(new IntegerResultNode(10))
                                          .setRank(100)
                                          .addAggregationResult(new SumAggregationResult()
                                                     .setSum(new IntegerResultNode(100))
                                                     .setExpression(new AttributeNode("s3"))))))
            .addChild(new Group().setId(new IntegerResultNode(9)).setRank(10))
            .addChild(new Group()
                      .setId(new IntegerResultNode(10))
                      .setRank(100)
                      .addAggregationResult(new SumAggregationResult()
                                 .setSum(new IntegerResultNode(100))
                                 .setExpression(new AttributeNode("s1")))
                      // dummy child would be picket up here
                      .addChild(new Group()
                                .setId(new IntegerResultNode(15))
                                .setRank(200)
                                .addAggregationResult(new SumAggregationResult()
                                           .setSum(new IntegerResultNode(100))
                                           .setExpression(new AttributeNode("s2")))))
            .addChild(new Group().setId(new IntegerResultNode(14)).setRank(10))
            .addChild(new Group()
                      .setId(new IntegerResultNode(15))
                      .setRank(5) // merged with 300 rank node
                      .addAggregationResult(new SumAggregationResult()
                                 .setSum(new IntegerResultNode(100))
                                 .setExpression(new AttributeNode("s1")))
                      .addChild(new Group().setId(new IntegerResultNode(19)).setRank(10))
                      .addChild(new Group()
                                .setId(new IntegerResultNode(20))
                                .setRank(5) // merged with 100 rank node
                                .addAggregationResult(new SumAggregationResult()
                                           .setSum(new IntegerResultNode(100))
                                           .setExpression(new AttributeNode("s2")))
                                .addChild(new Group().setId(new IntegerResultNode(19)).setRank(10))
                                .addChild(new Group()
                                          .setId(new IntegerResultNode(20))
                                          .setRank(500)
                                          .addAggregationResult(new SumAggregationResult()
                                                     .setSum(new IntegerResultNode(100))
                                                     .setExpression(new AttributeNode("s3")))))
                      .addChild(new Group().setId(new IntegerResultNode(24)).setRank(10))
                      .addChild(new Group()
                                .setId(new IntegerResultNode(25))
                                .setRank(300)
                                .addAggregationResult(new SumAggregationResult()
                                           .setSum(new IntegerResultNode(100))
                                           .setExpression(new AttributeNode("s2")))
                                .addChild(new Group().setId(new IntegerResultNode(24)).setRank(10))
                                .addChild(new Group()
                                          .setId(new IntegerResultNode(25))
                                          .setRank(400)
                                          .addAggregationResult(new SumAggregationResult()
                                                     .setSum(new IntegerResultNode(100))
                                                     .setExpression(new AttributeNode("s3"))))));

        Group expect = new Group()
            .addAggregationResult(new SumAggregationResult()
                       .setSum(new IntegerResultNode(200))
                       .setExpression(new AttributeNode("s0")))
            .addChild(new Group()
                      .setId(new IntegerResultNode(5))
                      .setRank(200)
                      .addAggregationResult(new SumAggregationResult()
                                 .setSum(new IntegerResultNode(200))
                                 .setExpression(new AttributeNode("s1")))
                      .addChild(new Group()
                                .setId(new IntegerResultNode(5))
                                .setRank(500)
                                .addAggregationResult(new SumAggregationResult()
                                           .setSum(new IntegerResultNode(100))
                                           .setExpression(new AttributeNode("s2")))
                                .addChild(new Group()
                                          .setId(new IntegerResultNode(5))
                                          .setRank(200)
                                          .addAggregationResult(new SumAggregationResult()
                                                     .setSum(new IntegerResultNode(100))
                                                     .setExpression(new AttributeNode("s3")))))
                      .addChild(new Group()
                                .setId(new IntegerResultNode(10))
                                .setRank(400)
                                .addAggregationResult(new SumAggregationResult()
                                           .setSum(new IntegerResultNode(100))
                                           .setExpression(new AttributeNode("s2")))
                                .addChild(new Group()
                                          .setId(new IntegerResultNode(10))
                                          .setRank(100)
                                          .addAggregationResult(new SumAggregationResult()
                                                     .setSum(new IntegerResultNode(100))
                                                     .setExpression(new AttributeNode("s3"))))))
            .addChild(new Group()
                      .setId(new IntegerResultNode(10))
                      .setRank(100)
                      .addAggregationResult(new SumAggregationResult()
                                 .setSum(new IntegerResultNode(200))
                                 .setExpression(new AttributeNode("s1")))
                      .addChild(new Group()
                                .setId(new IntegerResultNode(15))
                                .setRank(200)
                                .addAggregationResult(new SumAggregationResult()
                                           .setSum(new IntegerResultNode(200))
                                           .setExpression(new AttributeNode("s2")))
                                .addChild(new Group()
                                          .setId(new IntegerResultNode(15))
                                          .setRank(300)
                                          .addAggregationResult(new SumAggregationResult()
                                                     .setSum(new IntegerResultNode(100))
                                                     .setExpression(new AttributeNode("s3"))))))
            .addChild(new Group()
                      .setId(new IntegerResultNode(15))
                      .setRank(300)
                      .addAggregationResult(new SumAggregationResult()
                                 .setSum(new IntegerResultNode(200))
                                 .setExpression(new AttributeNode("s1")))
                      .addChild(new Group()
                                .setId(new IntegerResultNode(20))
                                .setRank(100)
                                .addAggregationResult(new SumAggregationResult()
                                           .setSum(new IntegerResultNode(200))
                                           .setExpression(new AttributeNode("s2")))
                                .addChild(new Group()
                                          .setId(new IntegerResultNode(20))
                                          .setRank(500)
                                          .addAggregationResult(new SumAggregationResult()
                                                     .setSum(new IntegerResultNode(100))
                                                     .setExpression(new AttributeNode("s3")))))
                      .addChild(new Group()
                                .setId(new IntegerResultNode(25))
                                .setRank(300)
                                .addAggregationResult(new SumAggregationResult()
                                           .setSum(new IntegerResultNode(100))
                                           .setExpression(new AttributeNode("s2")))
                                .addChild(new Group()
                                          .setId(new IntegerResultNode(25))
                                          .setRank(400)
                                          .addAggregationResult(new SumAggregationResult()
                                                     .setSum(new IntegerResultNode(100))
                                                     .setExpression(new AttributeNode("s3"))))));

        assertMerge(request, lhs, rhs, expect);
        assertMerge(request, rhs, lhs, expect);
    }

    private static void assertMerge(Grouping request, Group lhs, Group rhs, Group expect) {
        assertMerge(Arrays.asList(request.clone().setRoot(lhs.clone()),
                                  request.clone().setRoot(rhs.clone())),
                    expect);
    }

    private static void assertMerge(Grouping request, Group a, Group b, Group c, Group expect) {
        assertMerge(Arrays.asList(request.clone().setRoot(a.clone()),
                                  request.clone().setRoot(b.clone()),
                                  request.clone().setRoot(c.clone())),
                    expect);
    }

    private static void assertMerge(Grouping lhs, Grouping rhs, Group expect) {
        assertMerge(Arrays.asList(lhs, rhs), expect);
    }

    private static void assertMerge(List<Grouping> groupingList, Group expect) {
        Grouping tmp = groupingList.get(0).clone();
        for (int i = 1; i < groupingList.size(); ++i) {
            tmp.merge(groupingList.get(i));
        }
        tmp.postMerge();
        assertEquals(expect.toString(), tmp.getRoot().toString());
        assertEquals(expect, tmp.getRoot());
    }

}
