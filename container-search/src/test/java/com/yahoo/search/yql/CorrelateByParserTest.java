// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.yql;

import com.yahoo.component.chain.Chain;
import com.yahoo.prelude.IndexFacts;
import com.yahoo.prelude.IndexModel;
import com.yahoo.prelude.SearchDefinition;
import com.yahoo.prelude.query.CorrelateByItem;
import com.yahoo.prelude.query.Item;
import com.yahoo.prelude.query.NumericInItem;
import com.yahoo.prelude.query.SameElementItem;
import com.yahoo.prelude.query.StringInItem;
import com.yahoo.search.Query;
import com.yahoo.search.Searcher;
import com.yahoo.search.query.parser.Parsable;
import com.yahoo.search.query.parser.ParserEnvironment;
import com.yahoo.search.searchchain.Execution;
import org.junit.jupiter.api.Test;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class CorrelateByParserTest {

    private static final String CLAUSES = """
        zone_unit_double_signals contains sameElement(
            unit_id = $unit, zone_id = $zone, key = "price", value in (5200, 5400)),
        zone_unit_string_signals contains sameElement(
            unit_id = $unit, zone_id = $zone, key = "size", value contains "M"),
        unit_string_signals contains sameElement(
            unit_id = $unit, key = "Eco Friendly", value = "True")
        """;

    private static final String FIRST_PRESENT = """
        firstPresent(
            signals contains sameElement(unit_id = $unit, zone_id = $zone, key = "price", range(value, 5200, 5400)),
            signals contains sameElement(unit_id = $unit, zone_id = $zone, key = "price"),
            $zone in ("A", "B")),
        flat contains sameElement(unit_id = $unit, key = "Eco Friendly", value = "True")
        """;

    /** Every correlateBy body parsed successfully in this test, used to check that each one survives a YQL round trip. */
    private static final List<String> VALID_BODIES = List.of(
            CLAUSES,
            CLAUSES + ", $zone in (\"WAREHOUSE_A\", \"WAREHOUSE_B\")",
            "$zone in (\"B\"), " + CLAUSES,
            FIRST_PRESENT,
            FIRST_PRESENT + ", $unit in (\"u1\", \"u\\\"2\")",
            "a contains sameElement(unit_id = $u, zone_id = $z, key = 'price')",
            "a contains sameElement(unit_id = $u, key = 'a', key = 'b')",
            "a contains sameElement(unit_id = $u, key = 'a', key contains 'b', value > 3)",
            "firstPresent(a contains sameElement(unit_id = $u, zone_id = $z, key = 'price'), " +
            "a contains sameElement(unit_id = $u, zone_id = $z, key = 'price'), $z in ('A', 'B'))");

    private static Item parseWhere(YqlParser parser, String where) {
        return parser.parse(new Parsable().setQuery("select * from sources * where " + where)).getRoot();
    }

    private static CorrelateByItem parse(YqlParser parser, String body) {
        return (CorrelateByItem) parseWhere(parser, "correlateBy(" + body + ")");
    }

    /** Returns a parser without a schema, which accepts any field name. */
    private static YqlParser parser() {
        return new YqlParser(new ParserEnvironment());
    }

    /** Returns a parser with a schema, for tests which depend on field types. */
    private static YqlParser typedParser() {
        var definition = new SearchDefinition("test");
        definition.addCommand("price", "integer");
        definition.addCommand("title", "string");
        definition.addCommand("a", "index");
        definition.addCommand("a.unit_id", "string");
        definition.addCommand("a.zone_id", "string");
        definition.addCommand("a.key", "string");
        definition.addCommand("a.zone_number", "numerical");
        return new YqlParser(new ParserEnvironment().setIndexFacts(new IndexFacts(new IndexModel(definition))));
    }

    private static void assertRejected(String where, String expectedMessage) {
        var e = assertThrows(IllegalArgumentException.class, () -> parseWhere(parser(), where), where);
        assertEquals(expectedMessage, e.getMessage(), where);
    }

    @Test
    void bindsTheSameUnitAndZoneWithoutAddingAZoneToTheFlatClause() {
        var item = parse(parser(), CLAUSES + ", $zone in (\"WAREHOUSE_A\", \"WAREHOUSE_B\")");
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
    void acceptsADomainBeforeTheClauses() {
        assertEquals(List.of("B"), parse(parser(), "$zone in (\"B\"), " + CLAUSES).getAllowedValues().get("zone"));
    }

    @Test
    void readsTheLabelAnnotation() {
        var item = (CorrelateByItem) parseWhere(parser(), "({label: \"selection\"}correlateBy(" + CLAUSES + "))");
        assertEquals("selection", item.getLabel());
    }

    @Test
    void rejectsInvalidScopesAndCanBeReusedAfterAnError() {
        var parser = parser();
        for (String body : List.of(
                "a contains sameElement(unit_id = $u, key = 'x'), b contains sameElement(key = 'y')",
                "a contains sameElement(unit_id = $u, other_id = $u, key = 'x'), b contains sameElement(unit_id = $u, key = 'y')",
                "a contains sameElement(unit_id = $u), b contains sameElement(unit_id = $u, key = 'y')",
                CLAUSES + ", $unknown in ('A')",
                CLAUSES + ", $zone in ('A'), $zone in ('B')",
                CLAUSES + ", correlateBy(" + CLAUSES + ")",
                "")) {
            assertThrows(IllegalArgumentException.class, () -> parse(parser, body), body);
            assertEquals(3, parse(parser, CLAUSES).getItemCount());
        }
    }

    @Test
    void parsesFirstPresentAsSeparateMatchingAndPresenceQueries() {
        var item = parse(parser(), FIRST_PRESENT);
        assertEquals(3, item.getItemCount());
        assertEquals(Optional.of(new CorrelateByItem.FirstPresent("zone", List.of("A", "B"))), item.getFirstPresent(0));
        assertTrue(item.isPresenceClause(1));
        assertEquals(item.getBindings(0), item.getBindings(1));
        assertEquals(Map.of("key", List.of("price")), item.getExactValues(1));
        assertEquals(1, ((SameElementItem) item.getItem(1)).getItemCount());
        var clone = item.clone();
        assertEquals(item, clone);
        assertEquals(item.getFirstPresent(0), clone.getFirstPresent(0));
    }

    @Test
    void rejectsFirstPresentWithIncompatiblePresence() {
        var parser = parser();
        String matching = "a contains sameElement(unit_id = $u, zone_id = $z, key = 'price')";
        String flat = "flat contains sameElement(unit_id = $u, key = 'eco')";
        for (String body : List.of(
                "firstPresent(" + matching + ", " + flat + ", $z in ('A')), " + flat,
                "firstPresent(" + matching + ", " + matching.replace("a contains", "b contains") + ", $z in ('A')), " + flat,
                "firstPresent(" + matching + ", " + matching + ", $unknown in ('A')), " + flat,
                "firstPresent(" + matching + ", " + matching + "), " + flat)) {
            assertThrows(IllegalArgumentException.class, () -> parse(parser, body), body);
            assertEquals(3, parse(parser, CLAUSES).getItemCount());
        }
    }

    @Test
    void retainsBindingsForGroupingWithOneMatchingClause() {
        String clause = "a contains sameElement(unit_id = $u, zone_id = $z, key = 'price')";
        assertEquals(1, parse(parser(), clause).getItemCount());
        var fallback = parse(parser(), "firstPresent(" + clause + ", " + clause + ", $z in ('A', 'B'))");
        assertTrue(fallback.getFirstPresent(0).isPresent());
    }

    @Test
    void rejectsNumericVariableValuesWithAClearMessage() {
        assertRejected("correlateBy(" + CLAUSES + ", $zone in (1, 2))",
                       "The values of $zone must be quoted strings, such as $zone in (\"a\", \"b\")");
        assertRejected("correlateBy(firstPresent(a contains sameElement(unit_id = $u, zone_id = $z, key = 'p'), " +
                       "a contains sameElement(unit_id = $u, zone_id = $z, key = 'p'), $z in (1)))",
                       "The values of $z must be quoted strings, such as $z in (\"a\", \"b\")");
    }

    @Test
    void rejectsValuesForVariablesBoundToNumericFields() {
        var parser = typedParser();
        String clause = "a contains sameElement(unit_id = $u, zone_number = $z, key = 'p')";
        var e = assertThrows(IllegalArgumentException.class,
                             () -> parse(parser, clause + ", $z in ('1')"));
        assertEquals("Variable '$z' is bound to the numeric field 'a.zone_number'. " +
                     "Only variables bound to string fields can have a domain", e.getMessage());
        e = assertThrows(IllegalArgumentException.class,
                         () -> parse(parser, "firstPresent(" + clause + ", " + clause + ", $z in ('1'))"));
        assertEquals("Variable '$z' is bound to the numeric field 'a.zone_number'. " +
                     "Only variables bound to string fields can be used in firstPresent", e.getMessage());
        assertEquals(1, parse(parser, clause).getItemCount()); // A numeric variable without values is fine
    }

    @Test
    void rejectsVariablesOutsideCorrelateByWithAClearMessage() {
        String message = "'$x' can only be used inside correlateBy, as in unit_id = $unit in a sameElement clause, " +
                         "or $zone in (\"a\", \"b\")";
        assertRejected("title contains $x", message);
        assertRejected("title = $x", message);
        assertRejected("$x in (\"a\")", message);
        assertRejected("a contains sameElement(unit_id = $x, key contains 'k')", message);
        assertRejected("correlateBy(a contains sameElement(unit_id = $u, key contains $x))", message);
        assertRejected("correlateBy(a contains sameElement(unit_id = $u, key = 'k'), $u in (\"a\"), title contains $x)", message);
        assertRejected("weightedSet(title, {\"a\": 1}) and title contains $x", message);
    }

    @Test
    void variablesAreNotAcceptedAsParametersOrLiterals() {
        for (String yql : List.of(
                "select * from sources * where title contains 'a' limit $x",
                "select * from sources * where ({label: $x}title contains 'a')",
                "select * from sources * where title in ($x)",
                "select * from sources * where correlateBy(a contains sameElement(unit_id = $u, key = 'k'), $u in ($v))")) {
            assertThrows(IllegalArgumentException.class, () -> parser().parse(new Parsable().setQuery(yql)), yql);
        }
    }

    @Test
    void squareBracketListsAreNotMembershipSyntax() {
        for (String where : List.of(
                "price in [100, 200]",
                "title in [\"a\", \"b\"]",
                "id in []",
                "correlateBy(a contains sameElement(unit_id = $u, value in [1, 2]))",
                "correlateBy(a contains sameElement(unit_id = $u, key = 'k'), $u in [\"a\"])")) {
            assertThrows(IllegalArgumentException.class, () -> parseWhere(parser(), where), where);
        }
        assertEquals(NumericInItem.class, parseWhere(typedParser(), "price in (100, 200)").getClass());
        assertEquals(StringInItem.class, parseWhere(typedParser(), "title in (\"a\", \"b\")").getClass());
    }

    @Test
    void serializesToYqlWhichParsesToAnEqualItem() {
        for (String body : VALID_BODIES) {
            var parsed = parse(parser(), body);
            String yql = VespaSerializer.serialize(parsed);
            assertTrue(yql.startsWith("correlateBy("), yql);
            var reparsed = parseWhere(parser(), yql);
            assertEquals(parsed, reparsed, body + " was serialized as " + yql);
            assertEquals(yql, VespaSerializer.serialize(reparsed));
        }
    }

    @Test
    void serializesAStringListInAClause() {
        var parsed = parse(typedParser(), "a contains sameElement(unit_id = $u, key in ('x', 'y'))");
        String yql = VespaSerializer.serialize(parsed);
        assertEquals("correlateBy(a contains sameElement(unit_id = $u, key in (\"x\", \"y\")))", yql);
        assertEquals(parsed, parseWhere(typedParser(), yql));
    }

    @Test
    void serializesTheLabelAndExactValues() {
        var parsed = parse(parser(), "a contains sameElement(unit_id = $u, key = 'k', key contains 'p'), $u in (\"x\")");
        parsed.setLabel("selection");
        String yql = VespaSerializer.serialize(parsed);
        assertEquals("({label: \"selection\"}correlateBy(a contains sameElement(unit_id = $u, key = \"k\", " +
                     "key contains \"p\"), $u in (\"x\")))",
                     yql);
        var reparsed = (CorrelateByItem) parseWhere(parser(), yql);
        assertEquals("selection", reparsed.getLabel());
        assertEquals(parsed.getItemCount(), reparsed.getItemCount());
        assertEquals(parsed.getExactValues(0), reparsed.getExactValues(0));
    }

    @Test
    void correlateByQueryWithTracingSucceedsAndTracesTheQuery() {
        var execution = new Execution(new Chain<Searcher>(new MinimalQueryInserter()), Execution.Context.createContextStub());
        String yql = "select * from sources * where correlateBy(a contains sameElement(unit_id = $u, key = \"x\"))";
        var query = new Query("?tracelevel=2&yql=" + URLEncoder.encode(yql, StandardCharsets.UTF_8));
        var result = execution.search(query);
        assertNull(result.hits().getError());
        assertTrue(query.getModel().getQueryTree().getRoot() instanceof CorrelateByItem);
        String trace = query.getContext(false).getTrace().toString();
        assertTrue(trace.contains("correlateBy(a contains sameElement(unit_id = $u, key = \"x\"))"), trace);
        assertFalse(trace.contains("Invalid query"), trace);
    }

}
