// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.query;

import org.junit.jupiter.api.Test;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class CorrelateByItemTest {

    private SameElementItem clause(String field, String key) {
        var clause = new SameElementItem(field);
        clause.addItem(new WordItem(key, "key"));
        return clause;
    }

    private CorrelateByItem query() {
        var query = new CorrelateByItem();
        query.addClause(clause("zoned", "price"), Map.of("unit", "unit_id", "zone", "zone_id"));
        query.addClause(clause("flat", "eco"), Map.of("unit", "unit_id"));
        query.setAllowedValues("zone", List.of("A", "B"));
        return query;
    }

    @Test
    void serializesEachClausesBindingsAndTheZoneDomain() {
        var proto = ToProtobuf.convertFromQuery(query(), new SerializationContext(1.0)).getItemCorrelateBy();
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
    void clonesChildrenAndKeepsBindingMetadataImmutable() {
        var original = query();
        var clone = (CorrelateByItem) original.clone();
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
        second.addClause(clause("zoned", "price"), Map.of("unit", "unit_id", "zone", "other_zone"));
        second.addClause(clause("flat", "eco"), Map.of("unit", "unit_id"));
        second.setAllowedValues("zone", List.of("A", "B"));
        assertNotEquals(first, second);
        assertNotEquals(first.hashCode(), second.hashCode());
    }

    @Test
    void preservesExactValuesInSerializationAndClone() {
        var query = new CorrelateByItem();
        var values = new java.util.ArrayList<>(List.of("True"));
        query.addClause(clause("a", "eco"), Map.of("unit", "unit_id"), Map.of("value", values));
        query.addClause(clause("b", "size"), Map.of("unit", "unit_id"));
        values.add("true");
        var clone = (CorrelateByItem) query.clone();
        assertEquals(Map.of("value", List.of("True")), clone.getExactValues(0));
        assertThrows(UnsupportedOperationException.class, () -> clone.getExactValues(0).get("value").add("false"));
        var proto = ToProtobuf.convertFromQuery(clone, new SerializationContext(1.0)).getItemCorrelateBy();
        assertEquals("value", proto.getClauses(0).getExactValues(0).getField());
        assertEquals(List.of("True"), proto.getClauses(0).getExactValues(0).getValuesList());
    }

    @Test
    void refusesLegacyEncodingAndClausesWithoutBindings() {
        assertThrows(IllegalStateException.class, () -> query().encode(ByteBuffer.allocate(1024), new SerializationContext(1.0)));
        var query = new CorrelateByItem();
        assertThrows(IllegalArgumentException.class, () -> ToProtobuf.convertFromQuery(query, new SerializationContext(1.0)));
        assertThrows(IllegalArgumentException.class, () -> query.addClause(clause("a", "x"), Map.of()));
        query.addItem(clause("a", "x"));
        query.addItem(clause("b", "y"));
        assertThrows(IllegalArgumentException.class, () -> ToProtobuf.convertFromQuery(query, new SerializationContext(1.0)));
    }
}
