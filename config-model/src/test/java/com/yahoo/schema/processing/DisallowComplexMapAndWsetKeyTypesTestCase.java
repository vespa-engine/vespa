// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.processing;

import com.yahoo.schema.RankProfileRegistry;
import com.yahoo.schema.ApplicationBuilder;
import com.yahoo.schema.parser.ParseException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author lesters
 */
public class DisallowComplexMapAndWsetKeyTypesTestCase {

    @Test
    void requireThatComplexTypesForMapKeysFail() throws ParseException {
        assertThrows(IllegalArgumentException.class, () -> {
            testFieldType("map<mystruct,string>");
        });
    }

    @Test
    void requireThatComplexTypesForWsetFail() throws ParseException {
        assertThrows(IllegalArgumentException.class, () -> {
            testFieldType("weightedset<mystruct>");
        });
    }

    @Test
    void requireThatNestedComplexTypesForMapFail() throws ParseException {
        assertThrows(IllegalArgumentException.class, () -> {
            testFieldType("array<map<mystruct,string>>");
        });
    }

    @Test
    void requireThatNestedComplexValuesForMapSucceed() throws ParseException {
        testFieldType("array<map<string,mystruct>>");
    }

    @Test
    void requireThatNestedComplexTypesForWsetFail() throws ParseException {
        assertThrows(IllegalArgumentException.class, () -> {
            testFieldType("array<weightedset<mystruct>>");
        });
    }

    @Test
    void requireThatDeepNestedComplexTypesForMapFail() throws ParseException {
        assertThrows(IllegalArgumentException.class, () -> {
            testFieldType("map<string,map<mystruct,string>>");
        });
    }

    private void testFieldType(String fieldType) throws ParseException {
        RankProfileRegistry rankProfileRegistry = new RankProfileRegistry();
        ApplicationBuilder builder = new ApplicationBuilder(rankProfileRegistry);
        builder.addSchema(
                "search test {\n" +
                        "    document test { \n" +
                        "        struct mystruct {}\n" +
                        "        field a type " + fieldType + " {}\n" +
                        "    }\n" +
                        "}\n");
        builder.build(true);
    }

}
