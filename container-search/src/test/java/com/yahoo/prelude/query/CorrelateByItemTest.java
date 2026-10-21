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
import java.util.Set;

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
    void rejectingACyclicFirstPresentLeavesTheQueryUnchanged() {
        for (boolean cyclicPresence : new boolean[]{false, true}) {
            var query = query();
            var expected = protobuf(query);
            var bindings = Map.of("unit", "unit_id", "zone", "zone_id");
            var matching = clause("zoned", "price", bindings);
            var presence = clause("zoned", "price_present", bindings);
            var ancestor = cyclicPresence ? presence.item() : matching.item();
            ancestor.addItem(query);

            assertThrows(IllegalArgumentException.class,
                         () -> query.addFirstPresent(matching, presence, "zone", List.of("A", "B")));
            assertEquals(expected, protobuf(query));
            assertSame(ancestor, query.getParent());
            assertNull((cyclicPresence ? matching.item() : presence.item()).getParent());

            ancestor.removeItem(query);
            query.addFirstPresent(matching, presence, "zone", List.of("A", "B"));
            var updated = protobuf(query);
            assertEquals(4, updated.getClausesCount());
            assertEquals(1, updated.getFirstPresentCount());
            assertEquals(2, updated.getFirstPresent(0).getClause());
            assertEquals(3, updated.getFirstPresent(0).getPresence());
        }
    }

    @Test
    void rejectedIteratorReplacementKeepsTheOriginalParent() {
        var query = query();
        var original = query.getItem(0);
        var expected = protobuf(query);
        var ancestor = sameElement("zoned", "ancestor");
        ancestor.addItem(query);
        var iterator = query.getItemIterator();
        assertSame(original, iterator.next());

        assertThrows(IllegalArgumentException.class, () -> iterator.set(ancestor));
        assertSame(query, original.getParent());
        assertSame(original, query.getItem(0));
        assertEquals(expected, protobuf(query));

        ancestor.removeItem(query);
        var replacement = sameElement("zoned", "replacement");
        iterator.set(replacement);
        assertSame(query, replacement.getParent());
        assertNull(original.getParent());
        assertEquals(2, protobuf(query).getClausesCount());
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
    void refusesRemovingEitherClauseOfAFirstPresentPair() {
        for (int index : new int[]{0, 1}) {
            var query = firstPresentQuery();
            var original = query.clone();
            int position = index;
            assertThrows(IllegalArgumentException.class, () -> query.removeItem(position));
            assertThrows(IllegalArgumentException.class, () -> query.removeItem(query.getItem(position)));
            var iterator = query.getItemIterator();
            for (int i = 0; i <= index; ++i) iterator.next();
            assertThrows(IllegalArgumentException.class, iterator::remove);
            assertEquals(original, query);
            assertEquals(1, protobuf(query).getFirstPresentCount());
        }
    }

    @Test
    void firstPresentReplacementsMustKeepThePairedField() {
        for (int index : new int[]{0, 1}) {
            var query = firstPresentQuery();
            var original = query.clone();
            int position = index;
            assertThrows(IllegalArgumentException.class, () -> query.setItem(position, sameElement("other", "price")));
            assertThrows(IllegalArgumentException.class, () -> query.setItem(position, query.getItem(1 - position)));
            var iterator = query.getItemIterator();
            for (int i = 0; i <= index; ++i) iterator.next();
            assertThrows(IllegalArgumentException.class, () -> iterator.set(sameElement("other", "price")));
            assertEquals(original, query);
            iterator.set(sameElement("zoned", "replacement"));
            assertEquals(1, protobuf(query).getFirstPresentCount());
            query.setItem(position, sameElement("zoned", "another"));
            assertEquals(1, protobuf(query).getFirstPresentCount());
        }
    }

    @Test
    void canonicalizerRejectsAnEmptyFirstPresentClause() {
        for (int index : new int[]{0, 1}) {
            var query = firstPresentQuery();
            var clause = (SameElementItem) query.getItem(index);
            clause.setItem(0, new NullItem());
            var error = QueryCanonicalizer.canonicalize(new QueryTree(query));
            assertEquals("Cannot remove an empty clause of a firstPresent pair", error);
            assertEquals(3, query.getItemCount());
            assertTrue(query.isPresenceClause(1));
            assertTrue(query.getFirstPresent(0).isPresent());
        }
    }

    @Test
    void lowercasesExactValuesAndVariableValuesOnlyForFieldsWhichIgnoreCase() {
        var query = new CorrelateByItem();
        var bindings = Map.of("unit", "unit_id", "zone", "zone_id");
        query.addFirstPresent(new CorrelateByItem.Clause(sameElement("zoned", "price"), bindings, Map.of("key", List.of("Price"))),
                              clause("zoned", "price", bindings), "zone", List.of("North", "South"));
        query.addClause(new CorrelateByItem.Clause(sameElement("flat", "eco"), Map.of("unit", "unit_id"),
                                                   Map.of("value", List.of("True"))));
        query.setAllowedValues("zone", List.of("North"));
        query.setAllowedValues("unit", List.of("U1"));
        Set<String> ignoringCase = Set.of("zoned.key", "zoned.zone_id");
        query.lowercaseValues(ignoringCase::contains);
        assertEquals(Map.of("key", List.of("price")), query.getExactValues(0));
        assertEquals(Map.of("value", List.of("True")), query.getExactValues(2));
        assertEquals(List.of("north", "south"), query.getFirstPresent(0).get().values());
        assertEquals(List.of("north"), query.getAllowedValues().get("zone"));
        assertEquals(List.of("U1"), query.getAllowedValues().get("unit"));
    }

    @Test
    void refusesToLowercaseValuesOfAVariableBoundToFieldsWithDifferentCaseHandling() {
        var query = query();
        query.setAllowedValues("unit", List.of("U1"));
        var e = assertThrows(IllegalArgumentException.class, () -> query.lowercaseValues("zoned.unit_id"::equals));
        assertEquals("Variable '$unit' has values, but is bound both to fields which ignore case and fields which " +
                     "do not: [flat.unit_id, zoned.unit_id]", e.getMessage());
    }

}
