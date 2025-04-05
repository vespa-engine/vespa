// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query;

import com.ibm.icu.lang.UScript;
import com.ibm.icu.text.Collator;
import com.ibm.icu.text.RuleBasedCollator;
import com.ibm.icu.util.ULocale;
import com.yahoo.prelude.Index;
import com.yahoo.prelude.IndexFacts;
import com.yahoo.prelude.IndexModel;
import com.yahoo.prelude.SearchDefinition;
import com.yahoo.processing.IllegalInputException;
import com.yahoo.search.Query;
import com.yahoo.search.searchchain.Execution;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author baldersheim
 */
public class SortingTestCase {

    @Test
    void validAttributeName() {
        assertNotNull(Sorting.fromString("a"));
        assertNotNull(Sorting.fromString("_a"));
        assertNotNull(Sorting.fromString("+a"));
        assertNotNull(Sorting.fromString("-a"));
        assertNotNull(Sorting.fromString("a.b"));
        try {
            assertNotNull(Sorting.fromString("-1"));
            fail("'-1' should not be allowed as attribute name.");
        } catch (IllegalArgumentException e) {
            assertEquals(e.getMessage(), "Illegal attribute name '1' for sorting. Requires '[\\[]*[a-zA-Z_][\\.a-zA-Z0-9_-]*[\\]]*'");
        } catch (Exception e) {
            fail("I only expect 'IllegalArgumentException', not: + " + e.toString());
        }
    }

    @Test
    void aliasesAreRecognized() {
        Query query = new Query();
        var schema = new SearchDefinition("test");
        schema.addIndex(new Index("a"));
        schema.addAlias("aliasOfA", "a");
        Execution execution = new Execution(Execution.Context.createContextStub(new IndexFacts(new IndexModel(schema))));
        query.getModel().setExecution(execution);
        assertEquals("a", new Sorting("aliasOfA", query).fieldOrders().get(0).getFieldName());
    }

    @Test
    void requireThatChineseSortCorrect() {
        requireThatChineseHasCorrectRules(Collator.getInstance(new ULocale("zh")));
        Sorting ch = Sorting.fromString("uca(a,zh)");
        assertEquals(1, ch.fieldOrders().size());
        Sorting.FieldOrder fo = ch.fieldOrders().get(0);
        assertTrue(fo.getSorter() instanceof Sorting.UcaSorter);
        Sorting.UcaSorter uca = (Sorting.UcaSorter) fo.getSorter();
        requireThatChineseHasCorrectRules(uca.getCollator());
        Sorting.AttributeSorter sorter = fo.getSorter();
        assertTrue(sorter.compare("a", "b") < 0);
        assertTrue(sorter.compare("a", "a\u81EA") < 0);
        assertTrue(sorter.compare("\u81EA", "a") < 0);
    }

    private void requireThatArabicHasCorrectRules(Collator col) {
        final int reorderCodes [] = {UScript.ARABIC};
        assertEquals("6.2.0.0", col.getUCAVersion().toString());
        assertEquals("58.0.0.6", col.getVersion().toString());
        assertEquals(Arrays.toString(reorderCodes), Arrays.toString(col.getReorderCodes()));
        assertTrue(col.compare("a", "b") < 0);
        assertTrue(col.compare("a", "aس") < 0);
        assertFalse(col.compare("س", "a") < 0);

        assertEquals(" [reorder Arab]&ت<<ة<<<ﺔ<<<ﺓ&ي<<ى<<<ﯨ<<<ﯩ<<<ﻰ<<<ﻯ<<<ﲐ<<<ﱝ", ((RuleBasedCollator) col).getRules());
        assertFalse(col.compare("س", "a") < 0);
    }

    private void requireThatChineseHasCorrectRules(Collator col) {
        final int reorderCodes [] = {UScript.HAN};
        assertEquals("15.1.0.0", col.getUCAVersion().toString());
        assertEquals("153.121.45.0", col.getVersion().toString());
        assertEquals(Arrays.toString(reorderCodes), Arrays.toString(col.getReorderCodes()));

        assertNotEquals("", ((RuleBasedCollator) col).getRules());
    }

    @Test
    @Disabled
    void requireThatArabicSortCorrect() {
        requireThatArabicHasCorrectRules(Collator.getInstance(new ULocale("ar")));
        Sorting ar = Sorting.fromString("uca(a,ar)");
        assertEquals(1, ar.fieldOrders().size());
        Sorting.FieldOrder fo = ar.fieldOrders().get(0);
        assertTrue(fo.getSorter() instanceof Sorting.UcaSorter);
        Sorting.UcaSorter uca = (Sorting.UcaSorter) fo.getSorter();
        requireThatArabicHasCorrectRules(uca.getCollator());
        Sorting.AttributeSorter sorter = fo.getSorter();
        assertTrue(sorter.compare("a", "b") < 0);
        assertTrue(sorter.compare("a", "aس") < 0);
        assertTrue(sorter.compare("س", "a") < 0);
    }

    private static String encodedSpec(String spec) {
        return Sorting.fromString(spec).toSerialForm();
    }

    private static String failedSpec(String spec) {
        var e = assertThrows(IllegalInputException.class, () -> { Sorting.fromString(spec); });
        return e.getMessage();
    }

    @Test
    void requireExpectedEncodedSortSpec() {
        /*
         * If ValidateSortingSearcher doesn't update the Sorting instance with info from attribute config, e.g.
         * when using streaming search, then the default sort order is descending.
         */
        assertEquals("-a", encodedSpec("a"));
        assertEquals("-a", encodedSpec("-a"));
        assertEquals("+a", encodedSpec("+a"));
        assertEquals("-lowercase(a)", encodedSpec("lowercase(a)"));
        assertEquals("-lowercase(a)", encodedSpec("-lowercase(a)"));
        assertEquals("+lowercase(a)", encodedSpec("+lowercase(a)"));
        assertEquals("+lowercase(a)", encodedSpec("+LOWERCASE(a)"));
        assertEquals("-a", encodedSpec("raw(a)"));
        assertEquals("-a", encodedSpec("-raw(a)"));
        assertEquals("+a", encodedSpec("+raw(a)"));
        assertEquals("-a +b", encodedSpec("-a +b"));
        assertEquals("-a +lowercase(b)", encodedSpec("-a +lowercase(b)"));
    }

    @Test
    void requireMissingFunctionIsHandled() {
        assertEquals("-a", encodedSpec("missing(a,default)"));
        assertEquals("-missing(a,first)", encodedSpec("missing(a,first)"));
        assertEquals("-missing(a,first)", encodedSpec("-missing(a,first)"));
        assertEquals("-missing(lowercase(a),first)", encodedSpec("-missing(lowercase(a),first)"));
        assertEquals("+missing(a,first)", encodedSpec("+missing(a,first)"));
        assertEquals("+missing(a,first)", encodedSpec("+MISSING(a,FIRST)"));
        assertEquals("-missing(a,last)", encodedSpec("missing(a,last)"));
        assertEquals("-missing(a,as,default)", encodedSpec("missing(a,as,default)"));
        assertEquals("-missing(a,as,default)", encodedSpec("missing(a,as,\"default\")"));
        assertEquals("-missing(a,as,\"\")", encodedSpec("missing(a,as,)"));
        assertEquals("-missing(a,as,\"quoted \\\\ \\\" default\")",
                encodedSpec("missing(a,as,\"quoted \\\\ \\\" default\")"));
    }

    @Test
    void requireDetectBadSortSpec() {
        assertEquals("Expected ' ', got 'b' at [lowercase(a)][b]", failedSpec("lowercase(a)b"));
        assertEquals("Expected ')', end of spec reached at [lowercase(a][]", failedSpec("lowercase(a"));
        assertEquals("No sort function specified at [(][a)]", failedSpec("(a)"));
        assertEquals("Unknown sort function 'casefold' at [casefold(][a)]", failedSpec("casefold(a)"));
        assertEquals("Expected '\"', end of spec reached at [missing(a,as,\"default)][]",
                failedSpec("missing(a,as,\"default)"));
        assertEquals("Expected '\\' or '\"', got 'n' at [missing(a,as,\"bad \\][n default\")]",
                failedSpec("missing(a,as,\"bad \\n default\")"));
        assertEquals("Unknown missing policy 'before' at [missing(a,before][,default)]",
                failedSpec("missing(a,before,default)"));
    }
}
