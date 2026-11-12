// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation;

import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.config.model.NullConfigModelRegistry;
import com.yahoo.config.model.api.ValidationParameters;
import com.yahoo.config.model.api.ValidationParameters.CheckRouting;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.deploy.TestDeployState;
import com.yahoo.config.model.deploy.TestProperties;
import com.yahoo.config.model.test.MockApplicationPackage;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.content.utils.ContentClusterBuilder;
import com.yahoo.vespa.model.content.utils.DocType;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.util.List;
import java.util.logging.Level;

import static com.yahoo.config.model.test.TestUtil.joinLines;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Geir Storli
 */
public class ComplexFieldsValidatorTestCase {

    @Test
    void throws_exception_when_unsupported_complex_fields_have_struct_field_attributes() {
        Throwable exception = assertThrows(IllegalArgumentException.class, () -> {
            createModelAndValidate(joinLines("search test {",
                    "  document test {",
                    "    struct s { field f1 type array<int> {} }",
                    "    field struct_array type array<s> {",
                    "      struct-field f1 { indexing: attribute }",
                    "    }",
                    "    field struct_map type map<string,s> {",
                    "      struct-field key { indexing: attribute }",
                    "      struct-field value.f1 { indexing: attribute }",
                    "    }",
                    "  }",
                    "}"));
        });
        assertTrue(exception.getMessage().contains(getExpectedMessage("struct_array (struct_array.f1), struct_map (struct_map.value.f1)")));
    }

    @Test
    void throws_exception_when_nested_struct_array_is_specified_as_struct_field_attribute() {
        Throwable exception = assertThrows(IllegalArgumentException.class, () -> {
            createModelAndValidate(joinLines(
                    "schema test {",
                    "document test {",
                    "struct topic {",
                    "  field id type string {}",
                    "  field label type string {}",
                    "}",
                    "struct docTopic {",
                    "  field id type string {}",
                    "  field topics type array<topic> {}",
                    "}",
                    "field docTopics type array<docTopic> {",
                    "  indexing: summary",
                    "  struct-field id { indexing: attribute }",
                    "  struct-field topics { indexing: attribute }",
                    "}",
                    "}",
                    "}"));
        });
        assertEquals("For schema 'test': Field 'docTopics.topics' of type 'array<topic>' cannot be an attribute." +
                        " Instead specify the struct fields to be searchable as attribute",
                exception.getMessage());
    }

    @Test
    void throws_exception_when_struct_field_inside_nested_struct_array_is_specified_as_attribute() {
        Throwable exception = assertThrows(IllegalArgumentException.class, () -> {
            createModelAndValidate(joinLines(
                    "schema test {",
                    "document test {",
                    "struct item {",
                        "field name type string {}",
                        "field color type string {}",
                        "field type type string {}",
                    "}",
                    "struct itembox {",
                        "field items type array<item> {}",
                    "}",
                    "field cabinet type map<string, itembox> {",
                        "struct-field key { indexing: attribute }",
                        "struct-field value.items {",
                            "struct-field name  { indexing: attribute }",
                            "struct-field color { indexing: attribute }",
                        "}",
                    "}",
                    "}",
                    "}"));

        });
        assertTrue(exception.getMessage().contains(getExpectedMessage("cabinet (cabinet.value.items.name, cabinet.value.items.color)")));
    }

    private String getExpectedMessage(String unsupportedFields) {
        return "For cluster 'mycluster', search 'test': " +
                "The following complex fields do not support using struct field attributes: " +
                unsupportedFields + ". " +
                "Only supported for the following complex field types: array or map of struct with primitive types, map of primitive types";
    }

    private static final String unsupportedStructFieldIndexSchema = joinLines(
            "schema test {",
            "document test {",
            "struct topic {",
            "  field id type string {}",
            "  field count type int {}",
            "  field label type string {}",
            "  field desc type string {}",
            "}",
            "struct outer {",
            "  field inner type topic {}",
            "}",
            "field topics type array<topic> {",
            "  indexing: summary",
            "  struct-field id { indexing: index }",
            "  struct-field count { indexing: index | attribute }",
            "  struct-field label { indexing: index | attribute }",
            "  struct-field desc { indexing: attribute }",
            "}",
            "field single type topic {",
            "  indexing: summary",
            "  struct-field id { indexing: index }",
            "}",
            "field nested type array<outer> {",
            "  struct-field inner.id { indexing: index }",
            "}",
            "field grams type array<topic> {",
            "  struct-field id {",
            "    indexing: index",
            "    match: gram",
            "  }",
            "}",
            "}",
            "}");

    private static final String unsupportedStructFieldIndexFields =
            "topics (topics.count: type int is not string; " +
            "topics.label: having both 'indexing: index' and 'indexing: attribute' is not supported), " +
            "single (single.id: only supported in array of struct and map), " +
            "nested (nested.inner.id: only struct fields directly in the array elements can be indexed), " +
            "grams (grams.id: gram matching is not supported)";

    private static final String allStructFieldIndexFields =
            "topics (topics.id, topics.count, topics.label), single (single.id), nested (nested.inner.id), grams (grams.id)";

    private static final String removeSettingMessage =
            "Remove setting or change to 'indexing: attribute' if needed for matching.";

    @Test
    void throws_when_complex_fields_have_unsupported_struct_fields_with_index() {
        var exception = assertThrows(IllegalArgumentException.class, () -> {
            createModelAndValidate(unsupportedStructFieldIndexSchema, null, "allow_alone");
        });

        assertEquals("For cluster 'mycluster', schema 'test': " +
                     "The following complex fields have struct fields with 'indexing: index' which is not supported: " +
                     unsupportedStructFieldIndexFields + ". " + removeSettingMessage,
                     exception.getMessage());
    }

    @Test
    void throws_when_complex_fields_have_unsupported_struct_fields_with_index_also_when_allowing_attributes() {
        var exception = assertThrows(IllegalArgumentException.class, () -> {
            createModelAndValidate(unsupportedStructFieldIndexSchema, null, "allow");
        });

        assertEquals("For cluster 'mycluster', schema 'test': " +
                     "The following complex fields have struct fields with 'indexing: index' which is not supported: " +
                     "topics (topics.count: type int is not string), " +
                     "single (single.id: only supported in array of struct and map), " +
                     "nested (nested.inner.id: only struct fields directly in the array elements can be indexed), " +
                     "grams (grams.id: gram matching is not supported). " + removeSettingMessage,
                     exception.getMessage());
    }

    @Test
    void throws_when_struct_field_indexes_are_disallowed() {
        var exception = assertThrows(IllegalArgumentException.class, () -> {
            createModelAndValidate(unsupportedStructFieldIndexSchema, null, "disallow");
        });

        assertEquals("For cluster 'mycluster', schema 'test': " +
                     "The following complex fields have struct fields with 'indexing: index' which is not supported: " +
                     allStructFieldIndexFields + ". " + removeSettingMessage,
                     exception.getMessage());
    }

    private static class MyLogger implements DeployLogger {
        public StringBuilder message = new StringBuilder();
        @Override
        public void log(Level level, String message) {
            this.message.append(message);
        }
    }

    @Test
    void logs_warning_when_struct_field_indexes_are_ignored() throws IOException, SAXException {
        var logger = new MyLogger();
        createModelAndValidate(unsupportedStructFieldIndexSchema, logger, "ignore");
        assertTrue(logger.message.toString().contains(
                "For cluster 'mycluster', schema 'test': " +
                "The following complex fields have struct fields with 'indexing: index' which is not supported and has no effect: " +
                allStructFieldIndexFields + ". " + removeSettingMessage));
    }

    @Test
    void validation_passes_when_complex_fields_have_supported_struct_fields_with_index() throws IOException, SAXException {
        var logger = new MyLogger();
        createModelAndValidate(joinLines(
                "schema test {",
                "document test {",
                "struct topic {",
                "  field id type string {}",
                "  field label type string {}",
                "  field desc type string {}",
                "  field fragment type string {}",
                "}",
                "field topics type array<topic> {",
                "  indexing: summary",
                "  struct-field id { indexing: index }",
                "  struct-field label { indexing: attribute }",
                "  struct-field desc { indexing: attribute }",
                "  struct-field fragment { indexing: index }",
                "}",
                "field string_map type map<string,string> {",
                "  struct-field key { indexing: index }",
                "  struct-field value { indexing: index }",
                "}",
                "field topic_map type map<int,topic> {",
                "  struct-field key { indexing: attribute }",
                "  struct-field value.id { indexing: index }",
                "}",
                "}",
                "}"),
                               logger,
                               "allow_alone");
        assertFalse(logger.message.toString().contains("indexing: index"));
    }

    @Test
    void validation_passes_when_complex_fields_have_struct_fields_with_index_and_exact_match() throws IOException, SAXException {
        createModelAndValidate(joinLines(
                "schema test {",
                "  document test {",
                "    field nesteds type array<nested> {",
                "      struct-field foo {",
                "        indexing: index",
                "        match {",
                "          exact",
                "          exact-terminator: '@@'",
                "        }",
                "      }",
                "    }",
                "    struct nested {",
                "      field foo type string {}",
                "    }",
                "  }",
                "}"),
                               null,
                               "allow_alone");
    }

    @Test
    void validation_passes_when_only_supported_struct_field_attributes_are_used() throws IOException, SAXException {
        createModelAndValidate(joinLines("search test {",
                "  document test {",
                "    struct s1 {",
                "      field f1 type string {}",
                "      field f2 type int {}",
                "    }",
                "    struct s2 {",
                "      field f3 type string {}",
                "      field f4 type array<int> {}",
                "      field f5 type array<s1> {}",
                "    }",
                "    field struct_array type array<s2> {",
                "      struct-field f3 { indexing: attribute }",
                "    }",
                "    field struct_map type map<string,s2> {",
                "      struct-field key { indexing: attribute }",
                "      struct-field value.f3 { indexing: attribute }",
                "    }",
                "  }",
                "}"));
    }

    @Test
    void throws_exception_when_struct_field_index_has_literal_boost() {
        var exception = assertThrows(IllegalArgumentException.class, () ->
                createModelAndValidate(structFieldSchema("index", "rank: literal"), null, "allow_alone"));
        assertEquals("For schema 'test', field 'arr.f': 'rank: literal' is not supported for struct field indexes.",
                     exception.getMessage());
    }

    @Test
    void validation_passes_when_struct_field_attribute_has_literal_boost() throws IOException, SAXException {
        createModelAndValidate(structFieldSchema("attribute", "rank: literal"), null, "allow_alone");
    }

    private static String structFieldSchema(String indexing, String setting) {
        return joinLines("schema test {",
                         "  document test {",
                         "    struct s { field f type string {} }",
                         "    field arr type array<s> {",
                         "      struct-field f {",
                         "        indexing: " + indexing,
                         "        " + setting,
                         "      }",
                         "    }",
                         "  }",
                         "}");
    }

    @Test
    void throws_exception_when_struct_field_index_has_prefix_matching() {
        var exception = assertThrows(IllegalArgumentException.class, () ->
                createModelAndValidate(joinLines("schema test {",
                                                 "  document test {",
                                                 "    struct s { field f type string {} }",
                                                 "    field arr type array<s> {",
                                                 "      struct-field f {",
                                                 "        indexing: index",
                                                 "        match: prefix",
                                                 "      }",
                                                 "    }",
                                                 "  }",
                                                 "}"), null, "allow_alone"));
        assertEquals("For schema 'test', field 'arr.f': match/index:prefix is not supported for indexes.",
                     exception.getMessage());
    }

    @Test
    void throws_exception_when_struct_field_has_index_settings_in_streaming() {
        var exception = assertThrows(IllegalArgumentException.class, () ->
                createModelAndValidate(joinLines("schema test {",
                                                 "  document test {",
                                                 "    struct s { field f type string {} }",
                                                 "    field arr type array<s> {",
                                                 "      struct-field f {",
                                                 "        indexing: index",
                                                 "        stemming: none",
                                                 "        weight: 200",
                                                 "      }",
                                                 "    }",
                                                 "  }",
                                                 "}"),
                                       streamingServicesXml(), null, "allow_alone"));
        assertEquals("For cluster 'mycluster', schema 'test': The following struct fields have index settings, " +
                     "which are only supported for struct field indexes in indexed search: arr.f (stemming, weight).",
                     exception.getMessage());
    }

    private static void createModelAndValidate(String schema) throws IOException, SAXException {
        createModelAndValidate(schema, null);
    }

    private static void createModelAndValidate(String schema, DeployLogger logger) throws IOException, SAXException {
        createModelAndValidate(schema, logger, "disallow");
    }

    private static void createModelAndValidate(String schema,
                                               DeployLogger logger,
                                               String allowStructFieldIndex) throws IOException, SAXException {
        createModelAndValidate(schema, servicesXml(), logger, allowStructFieldIndex);
    }

    private static void createModelAndValidate(String schema,
                                               String servicesXml,
                                               DeployLogger logger,
                                               String allowStructFieldIndex) throws IOException, SAXException {
        DeployState deployState = createDeployState(servicesXml, schema, logger, allowStructFieldIndex);
        VespaModel model = new VespaModel(new NullConfigModelRegistry(), deployState);
        ValidationParameters validationParameters = new ValidationParameters(CheckRouting.FALSE);
        new Validation().validate(model, validationParameters, deployState);
    }

    private static DeployState createDeployState(String servicesXml,
                                                 String schema,
                                                 DeployLogger logger,
                                                 String allowStructFieldIndex) {
        ApplicationPackage app = new MockApplicationPackage.Builder()
                .withServices(servicesXml)
                .withSchemas(List.of(schema))
                .build();
        var properties = new TestProperties().allowStructFieldIndex(allowStructFieldIndex);
        var builder = TestDeployState.createBuilder()
                                     .applicationPackage(app)
                                     .properties(properties);
        if (logger != null) {
            builder.deployLogger(logger);
        }
        return builder.build();
    }

    private static String servicesXml() {
        return joinLines("<services version='1.0'>",
                new ContentClusterBuilder().getXml(),
                "</services>");
    }

    private static String streamingServicesXml() {
        return joinLines("<services version='1.0'>",
                new ContentClusterBuilder().docTypes(List.of(DocType.create("test", "streaming"))).getXml(),
                "</services>");
    }
}
