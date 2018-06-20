// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation;

import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.model.NullConfigModelRegistry;
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
        exceptionRule.expectMessage("For cluster 'mycluster', search 'test': " +
                "The following complex fields do not support using struct field attributes: " +
                "struct_array (struct_array.s1), struct_map (struct_map.key, struct_map.value.s1). " +
                "Only supported for the following complex field types: array or map of struct with primitive types, map of primitive types");

        createModelAndValidate(joinLines("search test {",
                "  document test {",
                "    struct s { field s1 type array<int> {} }",
                "    field struct_array type array<s> {",
                "      struct-field s1 { indexing: attribute }",
                "    }",
                "    field struct_map type map<string,s> {",
                "      struct-field key { indexing: attribute }",
                "      struct-field value.s1 { indexing: attribute }",
                "    }",
                "  }",
                "}"));
    }

    private static void createModelAndValidate(String searchDefinition) throws IOException, SAXException {
        DeployState deployState = createDeployState(servicesXml(), searchDefinition);
        VespaModel model = new VespaModel(new NullConfigModelRegistry(), deployState);
        Validation.validate(model, false, false, deployState);
    }

    private static DeployState createDeployState(String servicesXml, String searchDefinition) {
        ApplicationPackage app = new MockApplicationPackage.Builder()
                .withServices(servicesXml)
                .withSearchDefinition(searchDefinition)
                .build();
        return new DeployState.Builder().applicationPackage(app).build(true);
    }

    private static String servicesXml() {
        return joinLines("<services version='1.0'>",
                new ContentClusterBuilder().getXml(),
                "</services>");
    }
}
