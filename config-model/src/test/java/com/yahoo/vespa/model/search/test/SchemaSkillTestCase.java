// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.search.test;

import ai.vespa.search.skill.SchemaSkill;
import com.yahoo.config.model.application.provider.BaseDeployLogger;
import com.yahoo.config.model.deploy.TestProperties;
import com.yahoo.container.QrSearchersConfig;
import com.yahoo.schema.ApplicationBuilder;
import com.yahoo.schema.RankProfileRegistry;
import com.yahoo.schema.derived.Summaries;
import com.yahoo.schema.parser.ParseException;
import com.yahoo.search.config.SchemaInfoConfig;
import com.yahoo.search.schema.SchemaInfo;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the schema skill to catch changes in the schema info the skill depends on.
 *
 * @author ingrid
 */
public class SchemaSkillTestCase {

    @Test
    void testSchemaSkillFromDerivedSchemaInfo() throws ParseException {
        var registry = new RankProfileRegistry();
        var builder = new ApplicationBuilder(registry);
        builder.addSchema("""
                          schema test {
                              explanation: Test documents
                              document test {
                                  struct person {
                                      field name type string {}
                                      field age type int {}
                                  }
                                  field title type string {
                                      indexing: index | summary
                                      explanation: The title
                                  }
                                  field people type array<person> {
                                      indexing: summary
                                      struct-field name {
                                          indexing: attribute
                                          explanation: The name of the person
                                      }
                                  }
                                  field tags type map<string, int> {
                                      indexing: summary
                                      struct-field key {
                                          indexing: attribute
                                      }
                                  }
                                  field location type position {
                                      indexing: attribute
                                  }
                                  field prices type map<string, int> {
                                      fast-search-map-field by_currency
                                  }
                              }
                              fieldset default {
                                  fields: title
                              }
                              rank-profile relevance {
                                  explanation: Ranks by text relevance
                                  inputs {
                                      query(weight) double: 1.0
                                  }
                                  first-phase {
                                      expression: query(weight) * nativeRank(title)
                                  }
                              }
                          }
                          """);
        builder.build(true);
        var schema = builder.getSchema();
        var derived = new com.yahoo.schema.derived.SchemaInfo(schema, "index", registry,
                                                              new Summaries(schema, new BaseDeployLogger(), new TestProperties()));
        var config = new SchemaInfoConfig.Builder();
        derived.getConfig(config);
        var skill = new SchemaSkill(new SchemaInfo(config.build(), new QrSearchersConfig.Builder().build()));

        // Checks what the skill gets from the derived config, not the wording, which SchemaSkillTest covers
        String test = skill.schema("test").orElseThrow();
        assertTrue(test.contains("Test documents"), test);
        assertTrue(test.contains("\n- `title` (string; index; match: text): The title\n"), test);
        // Searchable struct and map fields are nested under their field, the others are left out
        assertTrue(test.contains("\n- `people` (array<struct person>;"), test);
        assertTrue(test.contains("\n  - `people.name` (string; attribute; match: word): The name of the person\n"), test);
        assertFalse(test.contains("people.age"), test);
        assertTrue(test.contains("\n- `tags` (map<string, int>;"), test);
        assertTrue(test.contains("\n  - `tags.key` (string; attribute; match: word)\n"), test);
        assertFalse(test.contains("tags.value"), test);
        // Generated fields are left out
        assertTrue(test.contains("\n- `location` (position; attribute)\n"), test);
        assertFalse(test.contains("location_zcurve"), test);
        // A map with fast map search is searched by its lookup, not through its generated lookup attribute
        assertTrue(test.contains("\n- `prices` (map<string, int>; searchable by lookup)\n"), test);
        assertTrue(test.contains("\n  - `prices.by_currency{key}` (lookup; key: string, value: int)\n"), test);
        assertFalse(test.contains("prices$by_currency"), test);
        assertTrue(test.contains("\n- `default`: `title`\n"), test);
        assertTrue(test.contains("\n- `relevance`: Ranks by text relevance\n"), test);
        assertTrue(test.contains("\n  - Input `query(weight)` (`double`)\n"), test);
    }

}
