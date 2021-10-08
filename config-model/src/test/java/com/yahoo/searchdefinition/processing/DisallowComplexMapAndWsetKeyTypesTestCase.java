// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.processing;

import com.yahoo.searchdefinition.RankProfileRegistry;
import com.yahoo.searchdefinition.SearchBuilder;
import com.yahoo.searchdefinition.parser.ParseException;
import org.junit.Test;

/**
 * @author lesters
 */
public class DisallowComplexMapAndWsetKeyTypesTestCase {

    @Test(expected = IllegalArgumentException.class)
    public void requireThatComplexTypesForMapKeysFail() throws ParseException {
        testFieldType("map<mystruct,string>");
    }

    @Test(expected = IllegalArgumentException.class)
    public void requireThatComplexTypesForWsetFail() throws ParseException {
        testFieldType("weightedset<mystruct>");
    }

    @Test(expected = IllegalArgumentException.class)
    public void requireThatNestedComplexTypesForMapFail() throws ParseException {
        testFieldType("array<map<mystruct,string>>");
    }

    @Test
    public void requireThatNestedComplexValuesForMapSucceed() throws ParseException {
        testFieldType("array<map<string,mystruct>>");
    }

    @Test(expected = IllegalArgumentException.class)
    public void requireThatNestedComplexTypesForWsetFail() throws ParseException {
        testFieldType("array<weightedset<mystruct>>");
    }

    @Test(expected = IllegalArgumentException.class)
    public void requireThatDeepNestedComplexTypesForMapFail() throws ParseException {
        testFieldType("map<string,map<mystruct,string>>");
    }

    private void testFieldType(String fieldType) throws ParseException {
        RankProfileRegistry rankProfileRegistry = new RankProfileRegistry();
        SearchBuilder builder = new SearchBuilder(rankProfileRegistry);
        builder.importString(
                "search test {\n" +
                        "    document test { \n" +
                        "        struct mystruct {}\n" +
                        "        field a type " + fieldType + " {}\n" +
                        "    }\n" +
                        "}\n");
        builder.build();
    }

}
