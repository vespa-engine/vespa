// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.yql;

import com.yahoo.prelude.query.CorrelateByItem;
import com.yahoo.prelude.query.SameElementItem;
import com.yahoo.search.query.parser.Parsable;
import com.yahoo.search.query.parser.ParserEnvironment;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

class CorrelateByParserTest {

    private static final String CLAUSES = """
        zone_unit_double_signals contains sameElement(
            unit_id = $unit, zone_id = $zone, key = "price", value in [5200, 5400]),
        zone_unit_string_signals contains sameElement(
            unit_id = $unit, zone_id = $zone, key = "size", value contains "M"),
        unit_string_signals contains sameElement(
            unit_id = $unit, key = "Eco Friendly", value = "True")
        """;

    private CorrelateByItem parse(YqlParser parser, String body) {
        return (CorrelateByItem) parser.parse(new Parsable().setQuery(
                "select * from sources * where correlateBy(" + body + ")")).getRoot();
    }

    @Test
    void bindsTheSameUnitAndZoneWithoutAddingAZoneToTheFlatClause() {
        var parser = new YqlParser(new ParserEnvironment());
        var item = parse(parser, CLAUSES + ", $zone in [\"WAREHOUSE_A\", \"WAREHOUSE_B\"]");
        assertEquals(3, item.getItemCount());
        assertEquals(Map.of("unit", "unit_id", "zone", "zone_id"), item.getBindings(0));
        assertEquals(item.getBindings(0), item.getBindings(1));
        assertEquals(Map.of("unit", "unit_id"), item.getBindings(2));
        assertEquals(List.of("WAREHOUSE_A", "WAREHOUSE_B"), item.getAllowedValues().get("zone"));
        assertEquals(Map.of("key", List.of("price")), item.getExactValues(0));
        assertEquals(Map.of("key", List.of("Eco Friendly"), "value", List.of("True")), item.getExactValues(2));
        assertEquals(2, ((SameElementItem) item.getItem(0)).getItemCount());
        assertEquals(2, ((SameElementItem) item.getItem(2)).getItemCount());
    }

    @Test
    void acceptsADomainBeforeTheClausesAndAnEmptyDomain() {
        var parser = new YqlParser(new ParserEnvironment());
        assertEquals(List.of("B"), parse(parser, "$zone in [\"B\"], " + CLAUSES).getAllowedValues().get("zone"));
        assertTrue(parse(parser, CLAUSES + ", $zone in []").getAllowedValues().get("zone").isEmpty());
    }

    @Test
    void rejectsInvalidScopesAndCanBeReusedAfterAnError() {
        var parser = new YqlParser(new ParserEnvironment());
        for (String body : List.of(
                "a contains sameElement(unit_id = $u, key = 'x'), b contains sameElement(key = 'y')",
                "a contains sameElement(unit_id = $u, other_id = $u, key = 'x'), b contains sameElement(unit_id = $u, key = 'y')",
                "a contains sameElement(unit_id = $u), b contains sameElement(unit_id = $u, key = 'y')",
                CLAUSES + ", $unknown in ['A']",
                CLAUSES + ", $zone in ['A'], $zone in ['B']",
                CLAUSES + ", $zone in [1]",
                CLAUSES + ", correlateBy(" + CLAUSES + ")",
                "")) {
            assertThrows(RuntimeException.class, () -> parse(parser, body), body);
            assertEquals(3, parse(parser, CLAUSES).getItemCount());
        }
        assertThrows(RuntimeException.class, () -> parser.parse(new Parsable().setQuery(
                "select * from sources * where a contains sameElement(unit_id = $u)")));
    }
    @Test
    void parsesFirstPresentAsSeparateMatchingAndPresenceQueries() {
        var parser = new YqlParser(new ParserEnvironment());
        var item = parse(parser, """
                firstPresent(
                    signals contains sameElement(unit_id = $unit, zone_id = $zone, key = "price", value in [5200, 5400]),
                    signals contains sameElement(unit_id = $unit, zone_id = $zone, key = "price"),
                    $zone in ["A", "B"]),
                flat contains sameElement(unit_id = $unit, key = "Eco Friendly", value = "True")
                """);
        assertEquals(3, item.getItemCount());
        assertEquals(Optional.of(new CorrelateByItem.FirstPresent("zone", List.of("A", "B"))), item.getFirstPresent(0));
        assertTrue(item.isPresenceClause(1));
        assertEquals(item.getBindings(0), item.getBindings(1));
        assertEquals(Map.of("key", List.of("price")), item.getExactValues(1));
        assertEquals(1, ((SameElementItem) item.getItem(1)).getItemCount());
        var clone = (CorrelateByItem) item.clone();
        assertEquals(item, clone);
        assertEquals(item.getFirstPresent(0), clone.getFirstPresent(0));
    }

    @Test
    void rejectsFirstPresentWithIncompatiblePresence() {
        var parser = new YqlParser(new ParserEnvironment());
        String matching = "a contains sameElement(unit_id = $u, zone_id = $z, key = 'price')";
        String flat = "flat contains sameElement(unit_id = $u, key = 'eco')";
        for (String body : List.of(
                "firstPresent(" + matching + ", " + flat + ", $z in ['A']), " + flat,
                "firstPresent(" + matching + ", " + matching.replace("a contains", "b contains") + ", $z in ['A']), " + flat,
                "firstPresent(" + matching + ", " + matching + ", $unknown in ['A']), " + flat)) {
            assertThrows(RuntimeException.class, () -> parse(parser, body), body);
            assertEquals(3, parse(parser, CLAUSES).getItemCount());
        }
    }

    @Test
    void retainsBindingsForGroupingWithOneMatchingClause() {
        var parser = new YqlParser(new ParserEnvironment());
        String clause = "a contains sameElement(unit_id = $u, zone_id = $z, key = 'price')";
        assertEquals(1, parse(parser, clause).getItemCount());
        var fallback = parse(parser, "firstPresent(" + clause + ", " + clause + ", $z in ['A', 'B'])");
        assertTrue(fallback.getFirstPresent(0).isPresent());
    }

}
