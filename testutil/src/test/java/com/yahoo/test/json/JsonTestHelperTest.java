// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.test.json;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * @author hakonhall
 */
public class JsonTestHelperTest {
    @Test
    public void normalize() {
        verifyNormalization("""
                            {"a": 1, "c": 2,
                              "b": [ {"n": 3, "m": 4}, 5 ]
                            }
                            """,
                            """
                            {
                              "a": 1,
                              "b": [
                                {
                                  "m": 4,
                                  "n": 3
                                },
                                5
                              ],
                              "c": 2
                            }""");

        verifyNormalization("[1,2]", """
                                     [
                                       1,
                                       2
                                     ]""");

        verifyNormalization("null", "null");
        verifyNormalization("{ \n}", "{}");
    }

    private static void verifyNormalization(String json, String normalizedJson) {
        assertEquals(normalizedJson, JsonTestHelper.normalize(json));
    }
}