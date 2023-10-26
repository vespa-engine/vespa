// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema;

import com.yahoo.search.query.profile.QueryProfileRegistry;
import com.yahoo.schema.parser.ParseException;
import com.yahoo.yolean.Exceptions;
import ai.vespa.rankingexpression.importer.configmodelview.ImportedMlModels;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests rank profiles with normalizers in bad places
 *
 * @author arnej
 */
public class NoNormalizersTestCase extends AbstractSchemaTestCase {

    void compileSchema(String schema) throws ParseException {
        RankProfileRegistry registry = new RankProfileRegistry();
        var qp = new QueryProfileRegistry();
        ApplicationBuilder builder = new ApplicationBuilder(registry, qp);
        builder.addSchema(schema);
        builder.build(true);
        for (RankProfile rp : registry.all()) {
            rp.compile(qp, new ImportedMlModels());
        }
    }

    @Test
    void requireThatNormalizerInFirstPhaseIsChecked() throws ParseException {
        try {
            compileSchema("""
                          search test {
                            document test { }
                            rank-profile p1 {
                              first-phase {
                                expression: normalize_linear(nativeRank)
                              }
                            }
                          }
                          """);
            fail();
        } catch (IllegalArgumentException e) {
            assertEquals("Rank profile 'p1' is invalid: " +
                         "Cannot use normalize_linear(nativeRank) from first-phase expression, only valid in global-phase expression",
                         Exceptions.toMessageString(e));
        }
    }

    @Test
    void requireThatNormalizerInSecondPhaseIsChecked() throws ParseException {
        try {
            compileSchema("""
                          search test {
                            document test {
                                field title type string {
                                    indexing: index
                                }
                            }
                            rank-profile p2 {
                                function foobar() {
                                    expression: 42 + reciprocal_rank(whatever, 1.0)
                                }
                                function whatever() {
                                    expression: fieldMatch(title)
                                }
                                first-phase {
                                    expression: nativeRank
                                }
                                second-phase {
                                    expression: foobar
                                }
                            }
                          }
                          """);
            fail();
        } catch (IllegalArgumentException e) {
            assertEquals("Rank profile 'p2' is invalid: " +
                         "Cannot use reciprocal_rank(whatever,1.0) from second-phase expression, only valid in global-phase expression",
                         Exceptions.toMessageString(e));
        }
    }

    @Test
    void requireThatNormalizerInMatchFeatureIsChecked() throws ParseException {
        try {
            compileSchema("""
                          search test {
                            document test { }
                            rank-profile p3 {
                                function foobar() {
                                    expression: normalize_linear(nativeRank)
                                }
                                first-phase {
                                    expression: nativeRank
                                }
                                match-features {
                                    nativeRank
                                    foobar
                                }
                            }
                          }
                          """);
            fail();
        } catch (IllegalArgumentException e) {
            assertEquals("Rank profile 'p3' is invalid: " +
                         "Cannot use normalize_linear(nativeRank) from match-feature foobar, only valid in global-phase expression",
                         Exceptions.toMessageString(e));
        }
    }

    @Test
    void requireThatNormalizerInSummaryFeatureIsChecked() throws ParseException {
        try {
            compileSchema("""
                          search test {
                            document test { }
                            rank-profile p4 {
                                function foobar() {
                                    expression: normalize_linear(nativeRank)
                                }
                                first-phase {
                                    expression: nativeRank
                                }
                                summary-features {
                                    nativeRank
                                    foobar
                                }
                            }
                          }
                          """);
            fail();
        } catch (IllegalArgumentException e) {
            assertEquals("Rank profile 'p4' is invalid: " +
                         "Cannot use normalize_linear(nativeRank) from summary-feature foobar, only valid in global-phase expression",
                         Exceptions.toMessageString(e));
        }
    }

    @Test
    void requireThatNormalizerInNormalizerIsChecked() throws ParseException {
        try {
            compileSchema("""
                          search test {
                            document test {
                                field title type string {
                                    indexing: index
                                }
                            }
                            rank-profile p5 {
                                function foobar() {
                                    expression: reciprocal_rank(nativeRank)
                                }
                                first-phase {
                                    expression: nativeRank
                                }
                                global-phase {
                                    expression: normalize_linear(fieldMatch(title)) + normalize_linear(foobar)
                                }
                            }
                          }
                          """);
            fail();
        } catch (IllegalArgumentException e) {
            assertEquals("Rank profile 'p5' is invalid: " +
                         "Cannot use reciprocal_rank(nativeRank) from normalizer input foobar, only valid in global-phase expression",
                         Exceptions.toMessageString(e));
        }
    }
}
