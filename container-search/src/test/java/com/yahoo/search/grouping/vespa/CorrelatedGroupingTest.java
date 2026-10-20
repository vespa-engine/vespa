// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.vespa;

import com.yahoo.component.ComponentId;
import com.yahoo.prelude.query.AndItem;
import com.yahoo.prelude.query.CorrelateByItem;
import com.yahoo.prelude.query.SameElementItem;
import com.yahoo.prelude.query.WordItem;
import com.yahoo.processing.IllegalInputException;
import com.yahoo.search.Query;
import com.yahoo.search.grouping.GroupingRequest;
import com.yahoo.search.grouping.request.AndPredicate;
import com.yahoo.search.grouping.request.AttributeValue;
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
import com.yahoo.search.searchchain.Execution;
import com.yahoo.search.searchchain.SearchChain;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

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
    @Test
    void onceperdocIsAcceptedAsTheWholeFilterOrAsTheLastCondition() {
        for (String filter : List.of(
                "onceperdoc(signals.unit)",
                "correlated(\"selection\", \"unit\", signals.unit) and onceperdoc(signals.unit)",
                "in(signals.key, \"size\") and (correlated(\"selection\", \"unit\", signals.unit) and onceperdoc(signals.unit))")) {
            var operation = GroupingOperation.fromString("all(group(signals.value) filter(" + filter + ") each(output(count())))");
            assertNotNull(operation.getFilterBy(), filter);
        }
    }

    @Test
    void onceperdocIsRejectedBeforeOtherConditionsAndInsideOrAndNot() {
        String message = "onceperdoc must be the last condition of a filter, as in filter(correlated(...) and onceperdoc(...)), " +
                         "and cannot be used inside 'or' or 'not'";
        for (String filter : List.of(
                "onceperdoc(signals.unit) and correlated(\"selection\", \"unit\", signals.unit)",
                "(correlated(\"selection\", \"unit\", signals.unit) and onceperdoc(signals.unit)) and in(signals.key, \"size\")",
                "onceperdoc(signals.unit) and onceperdoc(signals.zone)",
                "onceperdoc(signals.unit) or in(signals.key, \"size\")",
                "not onceperdoc(signals.unit)",
                "in(signals.key, \"size\") and not onceperdoc(signals.unit)")) {
            var e = assertThrows(IllegalArgumentException.class, () -> GroupingOperation.fromString(
                    "all(group(signals.value) filter(" + filter + ") each(output(count())))"), filter);
            assertEquals(message, e.getMessage(), filter);
        }
        var operation = GroupingOperation.fromString("all(group(signals.value) each(output(count())))");
        var once = new OncePerDocumentPredicate(List.of(new AttributeValue("signals.unit")));
        var correlated = new CorrelatedPredicate("selection", Map.of("unit", new AttributeValue("signals.unit")));
        assertThrows(IllegalArgumentException.class, () -> operation.setFilterBy(new AndPredicate(List.of(once, correlated))));
        assertNull(operation.getFilterBy());
        operation.setFilterBy(new AndPredicate(List.of(correlated, once)));
        assertNotNull(operation.getFilterBy());
    }

    @Test
    void acceptsCorrelatedFiltersMatchingALabeledCorrelateBy() {
        var query = queryWithGrouping("correlated(\"selection\", \"unit\", signals.unit, \"zone\", signals.zone)");
        CorrelatedFilterValidator.validate(query);
        var unlabeled = queryWithGrouping("in(signals.key, \"size\")");
        ((CorrelateByItem) unlabeled.getModel().getQueryTree().getRoot()).setLabel(null);
        CorrelatedFilterValidator.validate(unlabeled);
    }

    @Test
    void rejectsACorrelatedFilterWithAnUnknownLabel() {
        var query = queryWithGrouping("correlated(\"selction\", \"unit\", signals.unit)");
        var e = assertThrows(IllegalInputException.class,
                             () -> newExecution().search(query));
        assertEquals("Grouping filter correlated(\"selction\", \"unit\", signals.unit) refers to label 'selction', " +
                     "but the query has no correlateBy with that label. Label it as ({label:\"selction\"}correlateBy(...))",
                     e.getMessage());
    }

    @Test
    void rejectsACorrelatedFilterWhoseLabelIsUsedTwice() {
        var query = queryWithGrouping("in(signals.key, \"size\") and correlated(\"selection\", \"unit\", signals.unit)");
        var root = new AndItem();
        root.addItem(query.getModel().getQueryTree().getRoot());
        root.addItem(correlation("selection"));
        query.getModel().getQueryTree().setRoot(root);
        var e = assertThrows(IllegalInputException.class, () -> newExecution().search(query));
        assertEquals("Grouping filter correlated(\"selection\", \"unit\", signals.unit) refers to label 'selection', " +
                     "which is used by 2 correlateBy items in the query. Give each a different label", e.getMessage());
    }

    @Test
    void rejectsACorrelatedFilterWithAVariableTheCorrelateByDoesNotBind() {
        var query = queryWithGrouping("not correlated(\"selection\", \"unit\", signals.unit, \"size\", signals.size)");
        var e = assertThrows(IllegalInputException.class, () -> newExecution().search(query));
        assertEquals("Grouping filter correlated(\"selection\", \"size\", signals.size, \"unit\", signals.unit) uses " +
                     "variable 'size', which the correlateBy labeled 'selection' does not bind. It binds [unit, zone]",
                     e.getMessage());
    }

    @Test
    void checksCorrelatedFiltersInNestedGroupingLevels() {
        var query = new Query();
        query.getModel().getQueryTree().setRoot(correlation("selection"));
        GroupingRequest.newInstance(query).setRootOperation(GroupingOperation.fromString(
                "all(group(signals.key) each(all(group(signals.value) filter(correlated(\"other\", \"unit\", signals.unit)) " +
                "each(output(count())))))"));
        assertThrows(IllegalInputException.class, () -> CorrelatedFilterValidator.validate(query));
    }

    private static CorrelateByItem correlation(String label) {
        var clause = new SameElementItem("signals");
        clause.addItem(new WordItem("price", "key"));
        var correlation = new CorrelateByItem();
        correlation.addClause(new CorrelateByItem.Clause(clause, Map.of("unit", "unit_id", "zone", "zone_id")));
        correlation.setLabel(label);
        return correlation;
    }

    private static Query queryWithGrouping(String filter) {
        var query = new Query();
        query.getModel().getQueryTree().setRoot(correlation("selection"));
        GroupingRequest.newInstance(query).setRootOperation(GroupingOperation.fromString(
                "all(group(signals.value) filter(" + filter + ") each(output(count())))"));
        return query;
    }

    private static Execution newExecution() {
        return new Execution(new SearchChain(new ComponentId("grouping"), List.of(new GroupingExecutor())),
                             Execution.Context.createContextStub());
    }

}
