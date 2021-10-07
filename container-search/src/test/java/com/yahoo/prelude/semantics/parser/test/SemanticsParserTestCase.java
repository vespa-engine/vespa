// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.semantics.parser.test;

import java.util.Iterator;

import com.yahoo.javacc.UnicodeUtilities;
import com.yahoo.prelude.semantics.RuleBase;
import com.yahoo.prelude.semantics.RuleImporter;
import com.yahoo.prelude.semantics.parser.ParseException;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Tests parsing of semantic rules bases
 *
 * @author bratseth
 */
public class SemanticsParserTestCase {

    private final static String ROOT = "src/test/java/com/yahoo/prelude/semantics/parser/test/";

    @Test
    public void testRuleReading() throws java.io.IOException, ParseException {
        RuleBase rules=new RuleImporter().importFile(ROOT + "rules.sr");
        Iterator<?> i=rules.ruleIterator();
        assertEquals("[listing] [preposition] [place] -> listing:[listing] place:[place]!150",
                     i.next().toString());
        assertEquals("[listing] [place] +> place:[place]",
                     i.next().toString());
        assertEquals("[brand] -> brand:[brand]",
                     i.next().toString());
        assertEquals("[category] -> category:[category]",
                     i.next().toString());
        assertEquals("digital camera -> digicamera",
                     i.next().toString());
        assertEquals("(parameter.ranking='cat'), (parameter.ranking='cat0') -> one",i.next().toString());
        assertFalse(i.hasNext());

        i=rules.conditionIterator();
        assertEquals("[listing] :- restaurant, shop, cafe, hotel",
                     i.next().toString());
        assertEquals("[preposition] :- in, at, near",
                     i.next().toString());
        assertEquals("[place] :- geary",
                     i.next().toString());
        assertEquals("[brand] :- sony, dell",
                     i.next().toString());
        assertEquals("[category] :- digital camera, camera, phone",
                     i.next().toString());
        assertFalse(i.hasNext());

        assertTrue(rules.isDefault());
        assertEquals(ROOT + "semantics.fsa",rules.getAutomataFile());
    }

}
