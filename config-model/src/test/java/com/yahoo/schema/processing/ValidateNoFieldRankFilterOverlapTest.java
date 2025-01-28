// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.processing;

import com.yahoo.schema.ApplicationBuilder;
import com.yahoo.schema.parser.ParseException;
import com.yahoo.yolean.Exceptions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * @author vekterli
 */
public class ValidateNoFieldRankFilterOverlapTest {

    @Test
    void document_field_with_rank_filter_and_profile_filter_threshold_overlap_is_rejected() throws ParseException {
        try {
            var schema = """
                    search foo {
                        document foo {
                            field bar type string {
                                indexing: index
                                rank: filter
                            }
                        }
                        rank-profile ole_ivars {
                            rank bar {
                                filter-threshold: 0.03
                            }
                        }
                    }
                    """;
            ApplicationBuilder.createFromString(schema);
            fail("Expected exception");
        } catch (IllegalArgumentException e) {
            assertEquals("For schema 'foo', field 'bar': " +
                            "rank profile 'ole_ivars' declares field as `rank bar { filter-threshold:... }`, " +
                            "but field is also declared as `rank: filter`. These declarations are mutually exclusive.",
                    Exceptions.toMessageString(e));
        }
    }

    @Test
    void profile_field_with_rank_filter_and_filter_threshold_overlap_is_rejected() throws ParseException {
        try {
            var schema = """
                    search foo {
                        document foo {
                            field bar type string {
                                indexing: index
                            }
                        }
                        rank-profile ole_ivars {
                            rank bar: filter
                            rank bar {
                                filter-threshold: 0.03
                            }
                        }
                    }
                    """;
            ApplicationBuilder.createFromString(schema);
            fail("Expected exception");
        } catch (IllegalArgumentException e) {
            assertEquals("For schema 'foo', field 'bar': " +
                            "rank profile 'ole_ivars' declares field as `rank bar { filter-threshold:... }`, " +
                            "but field is also declared as `rank: filter`. These declarations are mutually exclusive.",
                    Exceptions.toMessageString(e));
        }
    }

    @Test
    void field_with_rank_filter_and_filter_threshold_is_ok_in_different_rank_profiles() throws ParseException {
        var schema = """
                search foo {
                    document foo {
                        field bar type string {
                            indexing: index
                        }
                    }
                    rank-profile ole_ivars {
                        rank bar: filter
                    }
                    rank-profile vikingarna {
                        rank bar {
                            filter-threshold: 0.03
                        }
                    }
                }
                """;
        ApplicationBuilder.createFromString(schema); // Should not throw
    }

    @Test
    void field_with_inherited_rank_filter_and_filter_threshold_overlap_is_rejected() throws ParseException {
        try {
            var schema = """
                    search foo {
                        document foo {
                            field bar type string {
                                indexing: index
                            }
                        }
                        rank-profile base_profile {
                            rank bar: filter
                        }
                        rank-profile child_profile inherits base_profile {
                            rank bar {
                                filter-threshold: 0.03
                            }
                        }
                    }
                    """;
            ApplicationBuilder.createFromString(schema);
            fail("Expected exception");
        } catch (IllegalArgumentException e) {
            assertEquals("For schema 'foo', field 'bar': " +
                            "rank profile 'child_profile' declares field as `rank bar { filter-threshold:... }`, " +
                            "but field is also declared as `rank: filter`. These declarations are mutually exclusive.",
                    Exceptions.toMessageString(e));
        }
    }

}
