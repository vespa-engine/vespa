// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema;

import com.yahoo.config.model.application.provider.BaseDeployLogger;
import com.yahoo.config.model.deploy.TestProperties;
import com.yahoo.schema.derived.SchemaInfo;
import com.yahoo.schema.derived.Summaries;
import com.yahoo.schema.parser.ParseException;
import com.yahoo.search.config.SchemaInfoConfig;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests explanations in schemas, fields and rank profiles.
 *
 * @author bratseth
 */
public class ExplanationTestCase {

    @Test
    void testExplanations() throws ParseException {
        var registry = new RankProfileRegistry();
        var builder = new ApplicationBuilder(registry);
        builder.addSchema("""
                          schema test {
                              explanation {
                                  Products in a web shop.

                                  Each document is a single product, identified by its SKU.
                                    - Indented lines keep their relative indentation.
                              }
                              document test {
                                  struct dimensions {
                                      field width type int {}
                                  }
                                  field title type string {
                                      indexing: index | summary
                                      explanation { The product title. }
                                  }
                                  field weight type float {
                                      indexing: attribute
                                      explanation:   Weight in {kg}, # not lbs.
                                  }
                                  field color type string { explanation: The color name }
                                  field size type dimensions {
                                      struct-field width {
                                          indexing: attribute
                                          explanation {
                                              Width in cm.
                                          }
                                      }
                                  }
                                  field explanation type string {
                                      indexing: attribute
                                  }
                              }
                              field title_lowercase type string {
                                  indexing: input title | lowercase | attribute
                                  explanation {
                                      Lowercased title, for {exact} # matching.
                                  }
                              }
                              rank-profile text {
                                  explanation {Ranks by text relevance only: Use for keyword queries.}
                                  first-phase {
                                      expression: bm25(title)
                                  }
                              }
                              rank-profile child inherits text {
                              }
                              rank-profile vector {
                                  explanation: Semantic similarity: Use for natural language queries.
                              }
                          }
                          """);
        builder.build(true);
        Schema schema = builder.getSchema();

        assertEquals(Optional.of("""
                                 Products in a web shop.

                                 Each document is a single product, identified by its SKU.
                                   - Indented lines keep their relative indentation."""),
                     schema.getExplanation());
        assertEquals(Optional.of("The product title."), schema.getConcreteField("title").getExplanation());
        assertEquals(Optional.of("Width in cm."), schema.getConcreteField("size").getStructField("width").getExplanation());
        assertEquals(Optional.of("Weight in {kg}, # not lbs."), schema.getConcreteField("weight").getExplanation());
        assertEquals(Optional.of("The color name"), schema.getConcreteField("color").getExplanation());
        assertEquals(Optional.empty(), schema.getConcreteField("explanation").getExplanation());
        assertEquals(Optional.of("Lowercased title, for {exact} # matching."),
                     schema.getConcreteField("title_lowercase").getExplanation());
        assertEquals(Optional.of("Ranks by text relevance only: Use for keyword queries."),
                     registry.get(schema, "text").getExplanation());
        assertEquals(Optional.empty(), registry.get(schema, "child").getExplanation());
        assertEquals(Optional.of("Semantic similarity: Use for natural language queries."),
                     registry.get(schema, "vector").getExplanation());
        assertEquals(Optional.empty(), registry.get(schema, "default").getExplanation());
    }

    @Test
    void testExplanationsInSchemaInfoConfig() throws ParseException {
        var registry = new RankProfileRegistry();
        var builder = new ApplicationBuilder(registry);
        builder.addSchema("""
                          schema parent {
                              document parent {
                                  field name type string {
                                      indexing: attribute
                                      explanation: The name of the parent
                                  }
                              }
                          }
                          """);
        builder.addSchema("""
                          schema child {
                              explanation: Children, each with a parent
                              document child {
                                  field parent_ref type reference<parent> {
                                      indexing: attribute
                                  }
                                  field title type string {
                                      indexing: index
                                      explanation {
                                          The title.
                                          Use for text matching.
                                      }
                                  }
                              }
                              import field parent_ref.name as parent_name {}
                              rank-profile text {
                                  explanation: Text relevance
                                  first-phase {
                                      expression: nativeRank(title)
                                  }
                              }
                          }
                          """);
        builder.build(true);
        Schema schema = builder.getSchema("child");
        var schemaInfo = new SchemaInfo(schema, "index", registry,
                                        new Summaries(schema, new BaseDeployLogger(), new TestProperties()));
        var configBuilder = new SchemaInfoConfig.Builder();
        schemaInfo.getConfig(configBuilder);
        var config = configBuilder.build().schema(0);

        assertEquals("Children, each with a parent", config.explanation());
        assertEquals("The title.\nUse for text matching.", fieldConfig("title", config).explanation());
        assertEquals("", fieldConfig("parent_ref", config).explanation());
        assertEquals("The name of the parent", fieldConfig("parent_name", config).explanation());
        assertEquals("Text relevance", rankProfileConfig("text", config).explanation());
        assertEquals("", rankProfileConfig("default", config).explanation());
    }

    private SchemaInfoConfig.Schema.Field fieldConfig(String name, SchemaInfoConfig.Schema schema) {
        return schema.field().stream().filter(field -> field.name().equals(name)).findFirst().orElseThrow();
    }

    private SchemaInfoConfig.Schema.Rankprofile rankProfileConfig(String name, SchemaInfoConfig.Schema schema) {
        return schema.rankprofile().stream().filter(profile -> profile.name().equals(name)).findFirst().orElseThrow();
    }

    @Test
    void testDuplicateExplanationIsRejected() {
        var builder = new ApplicationBuilder();
        var e = assertThrows(IllegalArgumentException.class, () -> builder.addSchema("""
                                                                                     schema test {
                                                                                         document test {
                                                                                         }
                                                                                         rank-profile text {
                                                                                             explanation { First }
                                                                                             explanation: Second
                                                                                         }
                                                                                     }
                                                                                     """));
        assertTrue(e.getMessage().contains("rank-profile 'text' error: duplicate explanation"), e.getMessage());
    }

}
