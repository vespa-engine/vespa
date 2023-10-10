// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.processing;

import com.yahoo.schema.ApplicationBuilder;
import com.yahoo.yolean.Exceptions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

public class MatchPhaseSettingsValidatorTestCase {

    private static String getMessagePrefix() {
        return "In search definition 'test', rank-profile 'default': match-phase attribute 'foo' ";
    }

    @Test
    void requireThatAttributeMustExists() throws Exception {
        try {
            var schema = """
                    search test {
                      document test {
                        field foo type int {
                          indexing: summary
                        }
                      }
                      rank-profile default {
                        match-phase {
                          attribute: foo
                          max-hits: 100
                        }
                      }
                    }
                    """;
            ApplicationBuilder.createFromString(schema);
            fail("Expected exception");
        }
        catch (IllegalArgumentException e) {
            assertEquals(getMessagePrefix() + "does not exists", Exceptions.toMessageString(e));
        }
    }

    @Test
    void requireThatAttributeMustBeNumeric() throws Exception {
        try {
            var schema = """
                    search test {
                      document test {
                        field foo type string {
                          indexing: attribute
                        }
                      }
                      rank-profile default {
                        match-phase {
                          attribute: foo
                          max-hits: 100
                        }
                      }
                    }
                    """;
            ApplicationBuilder.createFromString(schema);
            fail("Expected exception");
        }
        catch (IllegalArgumentException e) {
            assertEquals(getMessagePrefix() + "must be single value numeric, but it is 'string'",
                         Exceptions.toMessageString(e));
        }
    }

    @Test
    void requireThatAttributeMustBeSingleValue() throws Exception {
        try {
            var schema = """
                    search test {
                      document test {
                        field foo type array<int> {
                          indexing: attribute
                        }
                      }
                      rank-profile default {
                        match-phase {
                          attribute: foo
                          max-hits: 100
                        }
                      }
                    }
                    """;
            ApplicationBuilder.createFromString(schema);
            fail("Expected exception");
        }
        catch (IllegalArgumentException e) {
            assertEquals(getMessagePrefix() + "must be single value numeric, but it is 'Array<int>'",
                         Exceptions.toMessageString(e));
        }
    }

    @Test
    void requireThatAttributeMustHaveFastSearch() throws Exception {
        try {
            var schema = """
                    search test {
                      document test {
                        field foo type int {
                          indexing: attribute
                        }
                      }
                      rank-profile default {
                        match-phase {
                          attribute: foo
                          max-hits: 100
                        }
                      }
                    }
                    """;
            ApplicationBuilder.createFromString(schema);
            fail("Expected exception");
        }
        catch (IllegalArgumentException e) {
            assertEquals(getMessagePrefix() + "must be fast-search, but it is not",
                         Exceptions.toMessageString(e));
        }
    }

}
