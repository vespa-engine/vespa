// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.semantics.test;

import com.yahoo.search.Query;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Tests parameter matching and production
 *
 * @author bratseth
 */
public class ParameterTestCase extends RuleBaseAbstractTestCase {

    public ParameterTestCase() {
        super("parameter.sr");
    }

    /** Tests parameter literal matching */
    @Test
    public void testLiteralEquals() {
        assertSemantics("a","a");
        assertSemantics("RANK a foo:a","a&ranking=category");
        assertSemantics("a","a&ranking=somethingelse");
        assertSemantics("a","a&otherparam=category");
    }

    /** Tests parameter matching of larger */
    @Test
    public void testLarger() {
        assertSemantics("a","a");
        assertSemantics("AND a largepage","a&hits=11");
        assertSemantics("AND a largepage","a&hits=12");
    }

    /** Tests parameter containment matching */
    @Test
    public void testContainsAsList() {
        assertSemantics("a","a");
        assertSemantics("AND a intent:music","a&search=music");
        assertSemantics("AND a intent:music","a&search=music,books");
        assertSemantics("AND a intent:music","a&search=kanoos,music,books");
    }

    /** Tests parameter production */
    @Test
    public void testParameterProduction() {
        assertParameterSemantics("AND a b c","a b c","search","[letters, alphabet]");
        assertParameterSemantics("AND a c d","a c d","search","[letters, someletters]");
        assertParameterSemantics("+(AND a d e) -letter:c","a d e","search","[someletters]");
        assertParameterSemantics("AND a d f","a d f","rank-profile","foo");
        assertParameterSemantics("AND a f g","a f g","grouping.nolearning","true");
    }

    @Test
    public void testMultipleAlternativeParameterValuesInCondition() {
        assertInputRankParameterSemantics("one","foo","cat");
        assertInputRankParameterSemantics("one","foo","cat0");
        assertInputRankParameterSemantics("one","bar","cat");
        assertInputRankParameterSemantics("one","bar","cat0");
        assertInputRankParameterSemantics("AND one one","foo+bar","cat0");
        assertInputRankParameterSemantics("AND fuki sushi","fuki+sushi","cat0");
    }

    private void assertInputRankParameterSemantics(String producedQuery,String inputQuery,
                                               String rankParameterValue) {
        assertInputRankParameterSemantics(producedQuery,inputQuery,rankParameterValue,0);
    }

    private void assertInputRankParameterSemantics(String producedQuery,String inputQuery,
                                                   String rankParameterValue,int tracelevel) {
        Query query=new Query("?query=" + inputQuery + "&tracelevel=0&tracelevel.rules=" + tracelevel);
        query.getRanking().setProfile(rankParameterValue);
        query.properties().set("tracelevel.rules", tracelevel);
        assertSemantics(producedQuery, query);
    }

    private void assertParameterSemantics(String producedQuery,String inputQuery,
                                          String producedParameterName,String producedParameterValue) {
        Query query=assertSemantics(producedQuery,inputQuery);
        assertEquals(producedParameterValue,query.properties().getString(producedParameterName));
    }

}
