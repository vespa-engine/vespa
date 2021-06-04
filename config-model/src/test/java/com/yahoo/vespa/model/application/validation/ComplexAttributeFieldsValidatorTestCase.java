// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation;

import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.model.NullConfigModelRegistry;
import com.yahoo.config.model.api.ValidationParameters;
import com.yahoo.config.model.api.ValidationParameters.CheckRouting;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.test.MockApplicationPackage;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.content.utils.ContentClusterBuilder;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.xml.sax.SAXException;

import java.io.IOException;

import static com.yahoo.config.model.test.TestUtil.joinLines;

/**
 * @author geirst
 */
public class ComplexAttributeFieldsValidatorTestCase {

    @Rule
    public final ExpectedException exceptionRule = ExpectedException.none();

    @Test
    public void throws_exception_when_unsupported_complex_fields_have_struct_field_attributes() throws IOException, SAXException {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage(getExpectedMessage("struct_array (struct_array.f1), struct_map (struct_map.value.f1)"));
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
    }

    @Test
    public void throws_exception_when_nested_struct_array_is_specified_as_struct_field_attribute() throws IOException, SAXException {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage(getExpectedMessage("docTopics (docTopics.topics)"));
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
    }

    private String getExpectedMessage(String unsupportedFields) {
        return "For cluster 'mycluster', search 'test': " +
                "The following complex fields do not support using struct field attributes: " +
                unsupportedFields + ". " +
                "Only supported for the following complex field types: array or map of struct with primitive types, map of primitive types";
    }

    @Test
    public void validation_passes_when_only_supported_struct_field_attributes_are_used() throws IOException, SAXException {
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

    private static void createModelAndValidate(String searchDefinition) throws IOException, SAXException {
        DeployState deployState = createDeployState(servicesXml(), searchDefinition);
        VespaModel model = new VespaModel(new NullConfigModelRegistry(), deployState);
        ValidationParameters validationParameters = new ValidationParameters(CheckRouting.FALSE);
        Validation.validate(model, validationParameters, deployState);
    }

    private static DeployState createDeployState(String servicesXml, String searchDefinition) {
        ApplicationPackage app = new MockApplicationPackage.Builder()
                .withServices(servicesXml)
                .withSearchDefinition(searchDefinition)
                .build();
        return new DeployState.Builder().applicationPackage(app).build();
    }

    private static String servicesXml() {
        return joinLines("<services version='1.0'>",
                new ContentClusterBuilder().getXml(),
                "</services>");
    }
}
