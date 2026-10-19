// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.vespa;

import com.yahoo.search.grouping.request.CorrelatedPredicate;
import com.yahoo.search.grouping.request.DocumentCountAggregator;
import com.yahoo.search.grouping.request.GroupingOperation;
import com.yahoo.search.grouping.request.OncePerDocumentPredicate;
import com.yahoo.searchlib.expression.AttributeNode;
import com.yahoo.searchlib.aggregation.DocumentCountAggregationResult;
import com.yahoo.searchlib.expression.CorrelatedPredicateNode;
import com.yahoo.searchlib.expression.FilterExpressionNode;
import com.yahoo.searchlib.expression.OncePerDocumentPredicateNode;
import com.yahoo.vespa.objects.BufferSerializer;
import com.yahoo.vespa.objects.Identifiable;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CorrelatedGroupingTest {

    @Test
    void parsesAndConvertsCorrelatedRowBindings() {
        var operation = GroupingOperation.fromString("all(group(signals.value) filter(correlated(\"selection\", \"zone\", signals.zone, \"unit\", signals.unit)) each(output(count())))");
        var predicate = assertInstanceOf(CorrelatedPredicate.class, operation.getFilterBy());
        assertEquals("selection", predicate.getLabel());
        assertEquals(operation.toString(), GroupingOperation.fromString(operation.toString()).toString());
        assertEquals(predicate.toString(), predicate.copy().toString());
        var node = assertInstanceOf(CorrelatedPredicateNode.class, new ExpressionConverter().toFilterExpressionNode(predicate));
        assertEquals(List.of("unit", "zone"), node.getVariables());
        assertEquals(List.of(new AttributeNode("signals.unit"), new AttributeNode("signals.zone")), node.getFields());
        assertEquals("selection", node.getLabel());
        assertRoundTrip(node);
        assertEquals(node, node.clone());
        assertNotSame(node.getFields().get(0), node.clone().getFields().get(0));
    }

    @Test
    void parsesAndConvertsDocumentLocalTupleDeduplication() {
        var operation = GroupingOperation.fromString("all(group(signals.value) filter(onceperdoc(signals.unit, signals.value)) each(output(count())))");
        var predicate = assertInstanceOf(OncePerDocumentPredicate.class, operation.getFilterBy());
        assertEquals(operation.toString(), GroupingOperation.fromString(operation.toString()).toString());
        assertEquals(predicate.toString(), predicate.copy().toString());
        var node = assertInstanceOf(OncePerDocumentPredicateNode.class, new ExpressionConverter().toFilterExpressionNode(predicate));
        assertEquals(List.of(new AttributeNode("signals.unit"), new AttributeNode("signals.value")), node.getKeys());
        assertRoundTrip(node);
        assertEquals(node, node.clone());
        assertNotSame(node.getKeys().get(0), node.clone().getKeys().get(0));
    }

    @Test
    void rejectsAmbiguousOrEmptyBindingsAndAggregationKeys() {
        for (String predicate : List.of(
                "correlated(\"selection\", \"unit\", signals.unit, \"unit\", signals.other)",
                "correlated(\"\", \"unit\", signals.unit)",
                "correlated(\"selection\", \"\", signals.unit)",
                "correlated(\"selection\")",
                "onceperdoc()",
                "onceperdoc(sum(signals.value))",
                "onceperdoc(add(sum(signals.value), 1))")) {
            assertThrows(IllegalArgumentException.class, () -> GroupingOperation.fromString(
                    "all(group(signals.value) filter(" + predicate + ") each(output(count())))"), predicate);
        }
    }

    @Test
    void newPredicateNamesRemainLegalAttributeNames() {
        for (String name : List.of("correlated", "onceperdoc")) {
            var operation = GroupingOperation.fromString("all(group(" + name + ") each(output(count())))");
            assertEquals("all(group(" + name + ") each(output(count())))", operation.toString());
        }
    }

    private static void assertRoundTrip(FilterExpressionNode node) {
        var buffer = new BufferSerializer();
        node.serializeWithId(buffer);
        buffer.flip();
        assertEquals(node, Identifiable.create(buffer));
        assertEquals(0, buffer.remaining());
    }

    @Test
    void documentCountParsesCopiesConvertsAndSerializesWithElementCount() {
        var operation = GroupingOperation.fromString("all(group(signals.value) each(output(count(), doccount())))");
        operation.resolveLevel(1);
        var aggregator = assertInstanceOf(DocumentCountAggregator.class, operation.getChild(0).getOutput(1));
        assertEquals(operation.toString(), operation.copy(null).toString());
        assertEquals(aggregator.toString(), aggregator.copy().toString());
        var result = assertInstanceOf(DocumentCountAggregationResult.class,
                                      new ExpressionConverter().toAggregationResult(aggregator));
        result.setCount(123);
        var buffer = new BufferSerializer();
        result.serializeWithId(buffer);
        buffer.flip();
        var restored = assertInstanceOf(DocumentCountAggregationResult.class, Identifiable.create(buffer));
        assertEquals(123, restored.getCount());
        assertEquals(result, restored);
        assertEquals(0, buffer.remaining());
        result.merge(restored);
        assertEquals(246, result.getCount());
        aggregator.resolveLevel(2);
        assertThrows(IllegalArgumentException.class, () -> new ExpressionConverter().toAggregationResult(aggregator));
    }

    @Test
    void documentCountNameRemainsALegalAttributeName() {
        var operation = GroupingOperation.fromString("all(group(doccount) each(output(count())))");
        assertEquals("all(group(doccount) each(output(count())))", operation.toString());
    }
}
