// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.query;

import ai.vespa.searchlib.searchprotocol.protobuf.SearchProtocol;
import com.yahoo.search.query.QueryTree;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class CorrelateByItemTest {

    private SameElementItem sameElement(String field, String key) {
        var clause = new SameElementItem(field);
        clause.addItem(new WordItem(key, "key"));
        return clause;
    }

    private CorrelateByItem.Clause clause(String field, String key, Map<String, String> bindings) {
        return new CorrelateByItem.Clause(sameElement(field, key), bindings);
    }

    private CorrelateByItem query() {
        var query = new CorrelateByItem();
        query.addClause(clause("zoned", "price", Map.of("unit", "unit_id", "zone", "zone_id")));
        query.addClause(clause("flat", "eco", Map.of("unit", "unit_id")));
        query.setAllowedValues("zone", List.of("A", "B"));
        return query;
    }

    /** A matching clause and its presence clause on "zoned", followed by an ordinary clause on "flat". */
    private CorrelateByItem firstPresentQuery() {
        var query = new CorrelateByItem();
        var bindings = Map.of("unit", "unit_id", "zone", "zone_id");
        query.addFirstPresent(clause("zoned", "price", bindings), clause("zoned", "price_present", bindings),
                              "zone", List.of("A", "B"));
        query.addClause(clause("flat", "eco", Map.of("unit", "unit_id")));
        return query;
    }

    private SearchProtocol.ItemCorrelateBy protobuf(CorrelateByItem item) {
        return ToProtobuf.convertFromQuery(item, new SerializationContext(1.0)).getItemCorrelateBy();
    }

    @Test
    void serializesEachClausesBindingsAndTheZoneDomain() {
        var proto = protobuf(query());
        assertEquals(2, proto.getClausesCount());
        assertEquals("zoned", proto.getClauses(0).getQuery().getItemSameElement().getProperties().getIndex());
        assertEquals("unit", proto.getClauses(0).getBindings(0).getVariable());
        assertEquals("unit_id", proto.getClauses(0).getBindings(0).getField());
        assertEquals("zone", proto.getClauses(0).getBindings(1).getVariable());
        assertEquals(1, proto.getClauses(1).getBindingsCount());
        assertEquals("zone", proto.getDomains(0).getVariable());
        assertEquals(List.of("A", "B"), proto.getDomains(0).getValuesList());
    }

    @Test
    void serializesFirstPresentWithThePresenceClauseRightAfterIt() {
        var item = firstPresentQuery();
        assertEquals(Optional.of(new CorrelateByItem.FirstPresent("zone", List.of("A", "B"))), item.getFirstPresent(0));
        assertTrue(item.isPresenceClause(1));
        assertFalse(item.isPresenceClause(2));
        var proto = protobuf(item);
        assertEquals(3, proto.getClausesCount());
        assertEquals(1, proto.getFirstPresentCount());
        assertEquals(0, proto.getFirstPresent(0).getClause());
        assertEquals(1, proto.getFirstPresent(0).getPresence());
        assertEquals("zone", proto.getFirstPresent(0).getVariable());
        assertEquals(List.of("A", "B"), proto.getFirstPresent(0).getValuesList());
    }

    @Test
    void clonesChildrenAndKeepsBindingMetadataImmutable() {
        var original = query();
        var clone = original.clone();
        assertEquals(original, clone);
        assertEquals(original.hashCode(), clone.hashCode());
        assertNotSame(original.getItem(0), clone.getItem(0));
        assertThrows(UnsupportedOperationException.class, () -> clone.getBindings(0).put("zone", "wrong"));
        assertThrows(UnsupportedOperationException.class, () -> clone.getAllowedValues().get("zone").add("C"));
        clone.setAllowedValues("zone", List.of("B"));
        assertNotEquals(original, clone);
        assertEquals(List.of("A", "B"), original.getAllowedValues().get("zone"));
    }

    @Test
    void bindingMetadataAffectsEqualityAndHashing() {
        var first = query();
        var second = new CorrelateByItem();
        second.addClause(clause("zoned", "price", Map.of("unit", "unit_id", "zone", "other_zone")));
        second.addClause(clause("flat", "eco", Map.of("unit", "unit_id")));
        second.setAllowedValues("zone", List.of("A", "B"));
        assertNotEquals(first, second);
        assertNotEquals(first.hashCode(), second.hashCode());
    }

    @Test
    void firstPresentAffectsEquality() {
        var ordinary = new CorrelateByItem();
        var bindings = Map.of("unit", "unit_id", "zone", "zone_id");
        ordinary.addClause(clause("zoned", "price", bindings));
        ordinary.addClause(clause("zoned", "price_present", bindings));
        ordinary.addClause(clause("flat", "eco", Map.of("unit", "unit_id")));
        assertNotEquals(firstPresentQuery(), ordinary);
        assertEquals(firstPresentQuery(), firstPresentQuery());
        assertEquals(firstPresentQuery().hashCode(), firstPresentQuery().hashCode());
    }

    @Test
    void preservesExactValuesInSerializationAndClone() {
        var query = new CorrelateByItem();
        var values = new ArrayList<>(List.of("True"));
        query.addClause(new CorrelateByItem.Clause(sameElement("a", "eco"), Map.of("unit", "unit_id"), Map.of("value", values)));
        query.addClause(clause("b", "size", Map.of("unit", "unit_id")));
        values.add("true");
        var clone = query.clone();
        assertEquals(Map.of("value", List.of("True")), clone.getExactValues(0));
        assertThrows(UnsupportedOperationException.class, () -> clone.getExactValues(0).get("value").add("false"));
        var proto = protobuf(clone);
        assertEquals("value", proto.getClauses(0).getExactValues(0).getField());
        assertEquals(List.of("True"), proto.getClauses(0).getExactValues(0).getValuesList());
    }

    @Test
    void refusesLegacyEncodingEmptyItemsAndClausesWithoutBindings() {
        assertThrows(IllegalStateException.class, () -> query().encode(ByteBuffer.allocate(1024), new SerializationContext(1.0)));
        var query = new CorrelateByItem();
        assertThrows(IllegalArgumentException.class, () -> ToProtobuf.convertFromQuery(query, new SerializationContext(1.0)));
        assertThrows(IllegalArgumentException.class, () -> clause("a", "x", Map.of()));
        assertThrows(IllegalArgumentException.class, () -> clause("a", "x", Map.of("unit", " ")));
    }

    @Test
    void refusesFirstPresentWithDifferentBindingsFieldsOrAnUnboundVariable() {
        var query = new CorrelateByItem();
        var bindings = Map.of("unit", "unit_id", "zone", "zone_id");
        assertThrows(IllegalArgumentException.class, () -> query.addFirstPresent(
                clause("a", "x", bindings), clause("a", "y", Map.of("unit", "unit_id")), "zone", List.of("A")));
        assertThrows(IllegalArgumentException.class, () -> query.addFirstPresent(
                clause("a", "x", bindings), clause("b", "y", bindings), "zone", List.of("A")));
        assertThrows(IllegalArgumentException.class, () -> query.addFirstPresent(
                clause("a", "x", bindings), clause("a", "y", bindings), "other", List.of("A")));
        assertEquals(0, query.getItemCount());
    }

    @Test
    void refusesChildrenAddedWithoutBindingsAndStaysPrintable() {
        var query = query();
        assertThrows(IllegalArgumentException.class, () -> query.addItem(sameElement("c", "x")));
        assertThrows(IllegalArgumentException.class, () -> query.addItem(0, sameElement("c", "x")));
        assertThrows(IllegalArgumentException.class, () -> query.getItemIterator().add(sameElement("c", "x")));
        assertEquals(2, query.getItemCount());
        assertEquals("CORRELATE_BY{zone=[A, B]} {unit=unit_id, zone=zone_id}:(zoned:{key:price}) " +
                     "{unit=unit_id}:(flat:{key:eco})",
                     query.toString());
        assertEquals(query.hashCode(), query().hashCode());
        assertEquals(query, query());
    }

    @Test
    void replacingAClauseQueryKeepsItsBindingsAndRefusesOtherItems() {
        var query = query();
        query.setItem(0, sameElement("zoned", "weight"));
        assertEquals(Map.of("unit", "unit_id", "zone", "zone_id"), query.getBindings(0));
        assertThrows(IllegalArgumentException.class, () -> query.setItem(0, new WordItem("x", "key")));
        var iterator = query.getItemIterator();
        iterator.next();
        assertThrows(IllegalArgumentException.class, () -> iterator.set(new NullItem()));
        assertEquals("weight", ((WordItem) ((SameElementItem) query.getItem(0)).getItem(0)).getWord());
    }

    @Test
    void removingAClauseRemovesItsBindings() {
        var query = query();
        query.removeItem(0);
        assertEquals(1, query.getItemCount());
        assertEquals(Map.of("unit", "unit_id"), query.getBindings(0));
        assertEquals("CORRELATE_BY{zone=[A, B]} {unit=unit_id}:(flat:{key:eco})", query.toString());

        var other = query();
        assertTrue(other.removeItem(other.getItem(1)));
        assertEquals(Map.of("unit", "unit_id", "zone", "zone_id"), other.getBindings(0));
        assertFalse(other.removeItem(sameElement("none", "x")));
    }

    @Test
    void removingThePresenceClauseTurnsTheMatchingClauseIntoAnOrdinaryClause() {
        var query = firstPresentQuery();
        query.removeItem(1);
        assertEquals(2, query.getItemCount());
        assertEquals(Optional.empty(), query.getFirstPresent(0));
        assertFalse(query.isPresenceClause(0));
        assertEquals(0, protobuf(query).getFirstPresentCount());
        assertNotNull(query.toString());
        assertEquals(query, query.clone());
        assertEquals(query.hashCode(), query.clone().hashCode());
    }

    @Test
    void removingTheMatchingClauseWithAnIteratorTurnsThePresenceClauseIntoAnOrdinaryClause() {
        var query = firstPresentQuery();
        var iterator = query.getItemIterator();
        iterator.next();
        iterator.remove();
        assertEquals(2, query.getItemCount());
        assertFalse(query.isPresenceClause(0));
        assertEquals(Optional.empty(), query.getFirstPresent(0));
        assertEquals("price_present", ((WordItem) ((SameElementItem) query.getItem(0)).getItem(0)).getWord());
        assertEquals(0, protobuf(query).getFirstPresentCount());
        assertNotNull(query.toString());
    }

    @Test
    void canonicalizerRemovesAnEmptiedClauseTogetherWithItsBindings() {
        var query = firstPresentQuery();
        var presence = (SameElementItem) query.getItem(1);
        presence.setItem(0, new NullItem());
        var tree = new QueryTree(query);
        assertNull(QueryCanonicalizer.canonicalize(tree));
        assertSame(query, tree.getRoot());
        assertEquals(2, query.getItemCount());
        assertEquals(Optional.empty(), query.getFirstPresent(0));
        assertEquals(Map.of("unit", "unit_id"), query.getBindings(1));
        assertEquals(2, protobuf(query).getClausesCount());
        assertNotNull(query.toString());
        assertEquals(query, query.clone());
    }

}
