// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.semantics.test;

import com.yahoo.language.Language;
import com.yahoo.prelude.query.Item;
import com.yahoo.prelude.query.parser.AllParser;
import com.yahoo.prelude.query.parser.TestLinguistics;
import com.yahoo.search.Query;
import com.yahoo.search.query.parser.Parsable;
import com.yahoo.search.query.parser.ParserEnvironment;
import org.junit.Ignore;
import org.junit.Test;

public class SegmentSubstitutionTestCase extends RuleBaseAbstractTestCase {

    public SegmentSubstitutionTestCase() {
        super("substitution.sr");
    }

    @Test
    public void testBasicSubstitution() {
        Item a = parseQuery("firstsecond");
        Query q = new Query("?query=ignored&tracelevel=0&tracelevel.rules=0");
        q.getModel().getQueryTree().setRoot(a);

        assertSemantics("\"first third\"", q);
    }

    @Test
    public void testSubstitutionAndMoreTerms() {
        Item a = parseQuery("bcfirstsecondfg");
        Query q = new Query("?query=ignored&tracelevel=0&tracelevel.rules=0");
        q.getModel().getQueryTree().setRoot(a);

        assertSemantics("\"bc first third fg\"", q);
    }

    @Test
    public void testSubstitutionAndNot() {
        Item a = parseQuery("-firstsecond bc");
        Query q = new Query("?query=ignored&tracelevel=0&tracelevel.rules=0");
        q.getModel().getQueryTree().setRoot(a);

        assertSemantics("+bc -\"first third\"", q);
    }

    @Test
    public void testSubstitutionSomeNoise() {
        Item a = parseQuery("9270bcsecond2389");
        Query q = new Query("?query=ignored&tracelevel=0&tracelevel.rules=0");
        q.getModel().getQueryTree().setRoot(a);

        assertSemantics("\"9 2 7 0 bc third 2 3 8 9\"", q);
    }

    private static Item parseQuery(String query) {
        AllParser parser = new AllParser(new ParserEnvironment().setLinguistics(TestLinguistics.INSTANCE));
        return parser.parse(new Parsable().setQuery(query).setLanguage(Language.CHINESE_SIMPLIFIED)).getRoot();
    }

}
