// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.predicate;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author bjorncs
 */
public class PredicateQueryParserTest {

    @Test
    void require_that_json_is_correctly_parsed() {
        String json =
                "{" +
                        "   \"features\":[" +
                        "       {\"k\":\"k1\",\"v\":\"value1\",\"s\":\"0x1\"}," +
                        "       {\"k\":\"k2\",\"v\":\"value2\",\"s\":\"0x3\"}" +
                        "   ],\"rangeFeatures\":[" +
                        "       {\"k\":\"range1\",\"v\":123456789123,\"s\":\"0xffff\"}," +
                        "       {\"k\":\"range2\",\"v\":0,\"s\":\"0xffffffffffffffff\"}" +
                        "   ]" +
                        "}";

        PredicateQueryParser parser = new PredicateQueryParser();
        List<String> result = new ArrayList<>();
        parser.parseJsonQuery(
                json,
                (k, v, s) -> result.add(String.format("%s:%s:%#x", k, v, s)),
                (k, v, s) -> result.add(String.format("%s:%d:%#x", k, v, s)));

        assertEquals(result, Arrays.asList(
                "k1:value1:0x1", "k2:value2:0x3",
                "range1:123456789123:0xffff", "range2:0:0xffffffffffffffff"));
    }

}
