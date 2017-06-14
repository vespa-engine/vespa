// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.query.parser.test;


import com.yahoo.prelude.query.*;
import com.yahoo.prelude.query.parser.AbstractParser;
import com.yahoo.search.Query;
import com.yahoo.search.query.parser.Parsable;
import com.yahoo.search.query.parser.Parser;
import com.yahoo.search.query.parser.ParserEnvironment;
import com.yahoo.search.query.parser.ParserFactory;

/**
 * Tests guards against in single item phrases.
 *
 * @author Steinar Knutsen
 */
public class WashPhrasesTestCase extends junit.framework.TestCase {

    public WashPhrasesTestCase(String name) {
        super(name);
    }

    public void testSimplePositive() {
        PhraseItem root = new PhraseItem();

        root.addItem(new WordItem("abc"));
        assertEquals("abc", transformQuery(root));
    }

    public void testPositive1() {
        AndItem root = new AndItem();

        root.addItem(new WordItem("a"));
        PhraseItem embedded = new PhraseItem();

        embedded.addItem(new WordItem("bcd"));
        root.addItem(embedded);
        root.addItem(new WordItem("e"));
        assertEquals("AND a bcd e", transformQuery(root));
    }

    public void testPositive2() {
        AndItem root = new AndItem();

        root.addItem(new WordItem("a"));
        CompositeItem embedded = new AndItem();

        embedded.addItem(new WordItem("bcd"));
        CompositeItem phrase = new PhraseItem();

        phrase.addItem(new WordItem("def"));
        embedded.addItem(phrase);
        root.addItem(embedded);
        root.addItem(new WordItem("e"));
        assertEquals("AND a (AND bcd def) e", transformQuery(root));
    }

    public void testNoTerms() {
        assertNull(transformQuery("\"\""));
    }

    public void testNegative1() {
        assertEquals("\"abc def\"", transformQuery("\"abc def\""));
    }

    public void testNegative2() {
        assertEquals("AND a \"abc def\" b", transformQuery("a \"abc def\" b"));
    }

    public void testNegative3() {
        AndItem root = new AndItem();

        root.addItem(new WordItem("a"));
        CompositeItem embedded = new AndItem();

        embedded.addItem(new WordItem("bcd"));
        CompositeItem phrase = new PhraseItem();

        phrase.addItem(new WordItem("def"));
        phrase.addItem(new WordItem("ghi"));
        embedded.addItem(phrase);
        root.addItem(embedded);
        root.addItem(new WordItem("e"));
        assertEquals("AND a (AND bcd \"def ghi\") e", transformQuery(root));
    }

    private String transformQuery(String rawQuery) {
        Parser parser = ParserFactory.newInstance(Query.Type.ALL, new ParserEnvironment());
        Item root = parser.parse(new Parsable().setQuery(rawQuery)).getRoot();
        if (root instanceof NullItem) {
            return null;
        }
        return root.toString();
    }

    private String transformQuery(Item queryTree) {
        return AbstractParser.simplifyPhrases(queryTree).toString();
    }

}
