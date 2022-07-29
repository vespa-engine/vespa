// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.predicate;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author <a href="mailto:magnarn@yahoo-inc.com">Magnar Nedland</a>
 */

public class PredicateParserTest {

    @Test
    void requireThatParseErrorThrowsException() {
        try {
            Predicate.fromString("a in b");
            fail("Expected an exception");
        } catch (IllegalArgumentException e) {
            assertEquals("line 1:5 no viable alternative at input 'b'", e.getMessage());
        }
    }

    @Test
    void requireThatLexerErrorThrowsException() {
        try {
            Predicate.fromString("a-b in [b]");
            fail("Expected an exception");
        } catch (IllegalArgumentException e) {
            assertEquals("line 1:2 no viable alternative at character 'b'", e.getMessage());
        }
    }

    @Test
    void requireThatSingleValueLeafNodesParse() {
        assertParsesTo("a in [b]", "a in [b]");
        assertParsesTo("0 in [1]", "0 in [1]");
        assertParsesTo("in in [in]", "in in [in]");
        assertParsesTo("and in [or]", "and in [or]");
        assertParsesTo("not in [not]", "not in [not]");
        assertParsesTo("'-234' in ['+200']", "'-234' in ['+200']");
        assertParsesTo("string in ['!@#$%^&*()[]']", "'string' in ['!@#$%^&*()[]']");
        assertParsesTo("a in [b]", "a in [b]");
        assertParsesTo("string in ['foo\\\\_\"\\t\\n\\f\\rbar']",
                "string in ['foo\\\\_\\x22\\t\\n\\f\\rbar']");
        assertParsesTo("'\\xC3\\xB8' in [b]", "'ø' in [b]");
        assertParsesTo("'\\xEF\\xBF\\xBD' in [b]", "'\\xf8' in [b]");
        assertParsesTo("'\\xEF\\xBF\\xBD' in [b]", "'\\xef\\xbf\\xbd' in ['b']");
        assertParsesTo("'\\xE4\\xB8\\x9C\\xE8\\xA5\\xBF' in ['\\xE8\\x87\\xAA\\xE8\\xA1\\x8C\\xE8\\xBD\\xA6']",
                "'东西' in ['自行车']");
        assertParsesTo("true in [false]", "true in [false]");
    }

    @Test
    void requireThatMultiValueLeafNodesParse() {
        assertParsesTo("a in [b]", "a in [b]");
        assertParsesTo("0 in [1]", "0 in [1]");
        assertParsesTo("in in [and, in]", "in in [in, and]");
        assertParsesTo("a in [b, c, d, e, f]", "'a' in ['b', 'c', 'd', 'e', 'f']");
    }

    @Test
    void requireThatBothSingleAndDoubleQuotesWork() {
        assertParsesTo("a in [b]", "'a' in ['b']");
        assertParsesTo("a in [b]", "\"a\" in [\"b\"]");
        assertParsesTo("'a\\x27' in [b]", "'a\\'' in ['b']");
        assertParsesTo("'a\"' in [b]", "\"a\\\"\" in [\"b\"]");
    }

    @Test
    void requireThatRangeLeafNodesParse() {
        assertParsesTo("a in [0..100]", "a in [0..100]");
        assertParsesTo("0 in [..100]", "0 in [..100]");
        assertParsesTo("0 in [0..]", "0 in [0..]");
        assertParsesTo("0 in [..]", "0 in [..]");
        assertParsesTo("a in [-100..100]", "a in [-100..+100]");
        assertParsesTo("a in [-9223372036854775808..9223372036854775807]",
                "a in [-9223372036854775808..+9223372036854775807]");
    }

    @Test
    void requireThatRangePartitionsAreIgnored() {
        assertParsesTo("a in [0..100]", "a in [0..100 (a=0-99,a=100+[..0])]");
        assertParsesTo("a in [-100..0]", "a in [-100..0 (a=-0-99,a=-100+[..0])]");
        assertParsesTo("a in [-9223372036854775808..0]", "a in [-9223372036854775808..0 (a=-0-9223372036854775808)]");
        assertParsesTo("a in [2..8]", "a in [2..8 (a=0+[2..8])]");
        assertParsesTo("a in [0..8]", "a in [0..8 (a=0+[..8])]");
        assertParsesTo("a in [2..9]", "a in [2..9 (a=0+[2..])]");
    }

    @Test
    void requireThatNotInSetWorks() {
        assertParsesTo("a not in [b]", "a not in [b]");
    }

    @Test
    void requireThatConjunctionWorks() {
        assertParsesTo("a in [b] and c in [d]", "a in [b] and c in [d]");
        assertParsesTo("a in [b] and c in [d] and e in [f]", "a in [b] and c in [d] and e in [f]");
    }

    @Test
    void requireThatDisjunctionWorks() {
        assertParsesTo("a in [b] or c in [d]", "a in [b] or c in [d]");
        assertParsesTo("a in [b] or c in [d] or e in [f]", "a in [b] or c in [d] or e in [f]");
    }

    @Test
    void requireThatParenthesesWorks() {
        assertParsesTo("a in [b] or c in [d]",
                "(a in [b]) or (c in [d])");
        assertParsesTo("a in [b] or c in [d] or e in [f]",
                "(((a in [b]) or c in [d]) or e in [f])");
        assertParsesTo("(a in [b] and c in [d]) or e in [f]",
                "a in [b] and c in [d] or e in [f]");
        assertParsesTo("a in [b] and (c in [d] or e in [f])",
                "a in [b] and (c in [d] or e in [f])");
        assertParsesTo("a in [b] and (c in [d] or e in [f]) and g in [h]",
                "a in [b] and (c in [d] or e in [f]) and g in [h]");
    }

    @Test
    void requireThatNotOutsideParenthesesWorks() {
        assertParsesTo("a not in [b]", "not (a in [b])");
    }

    @Test
    void requireThatConjunctionsCanGetMoreThanTwoChildren() {
        Predicate p = Predicate.fromString("a in [b] and c in [d] and e in [f] and g in [h]");
        assertTrue(p instanceof Conjunction);
        assertEquals(4, ((Conjunction) p).getOperands().size());
    }

    @Test
    void requireThatDisjunctionsCanGetMoreThanTwoChildren() {
        Predicate p = Predicate.fromString("a in [b] or c in [d] or e in [f] or g in [h]");
        assertTrue(p instanceof Disjunction);
        assertEquals(4, ((Disjunction) p).getOperands().size());
    }

    @Test
    void requireThatBooleanCanBeParsed() {
        assertParsesTo("true", "true");
        assertParsesTo("false", "false");
        assertParsesTo("true or false", "true or false");
        assertParsesTo("false and true", "false and true");
    }

    private static void assertParsesTo(String expected, String predicate_str) {
        assertEquals(expected, // TODO: Predicate.fromString(expected)
                     Predicate.fromString(predicate_str).toString());
    }
}
