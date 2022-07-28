// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation;

import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.config.model.NullConfigModelRegistry;
import com.yahoo.config.model.api.ValidationParameters;
import com.yahoo.config.model.api.ValidationParameters.CheckRouting;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.test.MockApplicationPackage;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.content.utils.ContentClusterBuilder;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.util.List;
import java.util.logging.Level;

import static com.yahoo.config.model.test.TestUtil.joinLines;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author geirst
 */
public class ComplexFieldsValidatorTestCase {

    @Test
    void throws_exception_when_unsupported_complex_fields_have_struct_field_attributes() throws IOException, SAXException {
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
    void throws_exception_when_nested_struct_array_is_specified_as_struct_field_attribute() throws IOException, SAXException {
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
        assertTrue(exception.getMessage().contains(getExpectedMessage("docTopics (docTopics.topics)")));
    }

    private String getExpectedMessage(String unsupportedFields) {
        return "For cluster 'mycluster', search 'test': " +
                "The following complex fields do not support using struct field attributes: " +
                unsupportedFields + ". " +
                "Only supported for the following complex field types: array or map of struct with primitive types, map of primitive types";
    }

    private class MyLogger implements DeployLogger {
        public StringBuilder message = new StringBuilder();
        @Override
        public void log(Level level, String message) {
            this.message.append(message);
        }
    }

    @Test
    void logs_warning_when_complex_fields_have_struct_fields_with_index() throws IOException, SAXException {
        var logger = new MyLogger();
        createModelAndValidate(joinLines(
                "schema test {",
                "document test {",
                "struct topic {",
                "  field id type string {}",
                "  field label type string {}",
                "  field desc type string {}",
                "}",
                "field topics type array<topic> {",
                "  indexing: summary",
                "  struct-field id { indexing: index }",
                "  struct-field label { indexing: index | attribute }",
                "  struct-field desc { indexing: attribute }",
                "}",
                "}",
                "}"), logger);
        assertThat(logger.message.toString().contains(
                "For cluster 'mycluster', schema 'test': " +
                        "The following complex fields have struct fields with 'indexing: index' which is not supported and has no effect: " +
                        "topics (topics.id, topics.label). " +
                        "Remove setting or change to 'indexing: attribute' if needed for matching."));
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

    private static void createModelAndValidate(String schema) throws IOException, SAXException {
        createModelAndValidate(schema, null);
    }

    private static void createModelAndValidate(String schema, DeployLogger logger) throws IOException, SAXException {
        DeployState deployState = createDeployState(servicesXml(), schema, logger);
        VespaModel model = new VespaModel(new NullConfigModelRegistry(), deployState);
        ValidationParameters validationParameters = new ValidationParameters(CheckRouting.FALSE);
        new Validation().validate(model, validationParameters, deployState);
    }

    private static DeployState createDeployState(String servicesXml, String schema, DeployLogger logger) {
        ApplicationPackage app = new MockApplicationPackage.Builder()
                .withServices(servicesXml)
                .withSchemas(List.of(schema))
                .build();
        var builder = new DeployState.Builder().applicationPackage(app);
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
}
