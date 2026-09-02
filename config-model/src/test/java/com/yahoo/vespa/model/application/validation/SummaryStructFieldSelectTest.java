// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation;

import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.model.NullConfigModelRegistry;
import com.yahoo.config.model.api.ValidationParameters;
import com.yahoo.config.model.api.ValidationParameters.CheckRouting;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.deploy.TestDeployState;
import com.yahoo.config.model.test.MockApplicationPackage;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.content.utils.ContentClusterBuilder;
import com.yahoo.vespa.model.content.utils.DocType;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.util.List;

import static com.yahoo.config.model.test.TestUtil.joinLines;

/**
 * Tests that a struct field selection ("struct-field" in a document-summary) is accepted in every mode,
 * whether or not the selected struct fields are struct field attributes. When they are, the selection is
 * applied by the attribute combiner; when they are not, the summary field is filled from the stored
 * document and the selection is applied there. The selection itself is validated in
 * {@link com.yahoo.schema.processing.SummaryStructFieldSelectValidator}.
 *
 * @author arnej
 */
public class SummaryStructFieldSelectTest {

    /** A struct field selection over sub-fields which are struct field attributes. */
    private static String schemaWithAttributes() {
        return joinLines("schema test {",
                         "  document test {",
                         "    struct elem {",
                         "      field name type string {}",
                         "      field weight type int {}",
                         "    }",
                         "    field elem_array type array<elem> {",
                         "      indexing: summary",
                         "      struct-field name { indexing: attribute }",
                         "      struct-field weight { indexing: attribute }",
                         "    }",
                         "  }",
                         "  document-summary sel {",
                         "    summary elem_array {",
                         "      struct-field: name",
                         "    }",
                         "  }",
                         "}");
    }

    /** The same selection, but the sub-fields are not attributes, as in a streaming schema. */
    private static String schemaWithoutAttributes() {
        return joinLines("schema test {",
                         "  document test {",
                         "    struct elem {",
                         "      field name type string {}",
                         "      field weight type int {}",
                         "    }",
                         "    field elem_array type array<elem> {",
                         "      indexing: summary",
                         "    }",
                         "  }",
                         "  document-summary sel {",
                         "    summary elem_array {",
                         "      struct-field: name",
                         "    }",
                         "  }",
                         "}");
    }

    @Test
    void selection_without_struct_field_attributes_is_allowed_for_indexed_search() throws IOException, SAXException {
        createModelAndValidate(schemaWithoutAttributes(), DocType.index("test"));
    }

    @Test
    void selection_without_struct_field_attributes_is_allowed_for_streaming_search() throws IOException, SAXException {
        createModelAndValidate(schemaWithoutAttributes(), DocType.streaming("test"));
    }

    @Test
    void selection_over_struct_field_attributes_is_allowed_for_indexed_search() throws IOException, SAXException {
        createModelAndValidate(schemaWithAttributes(), DocType.index("test"));
    }

    @Test
    void selection_over_struct_field_attributes_is_allowed_for_streaming_search() throws IOException, SAXException {
        createModelAndValidate(schemaWithAttributes(), DocType.streaming("test"));
    }

    private static void createModelAndValidate(String schema, DocType docType) throws IOException, SAXException {
        DeployState deployState = createDeployState(servicesXml(docType), schema);
        VespaModel  model = new VespaModel(new NullConfigModelRegistry(), deployState);
        new Validation().validate(model, new ValidationParameters(CheckRouting.FALSE), deployState);
    }

    private static DeployState createDeployState(String servicesXml, String schema) {
        ApplicationPackage app = new MockApplicationPackage.Builder()
                .withServices(servicesXml)
                .withSchemas(List.of(schema))
                .build();
        return TestDeployState.createBuilder().applicationPackage(app).build();
    }

    private static String servicesXml(DocType docType) {
        return joinLines("<services version='1.0'>",
                         new ContentClusterBuilder().docTypes(List.of(docType)).getXml(),
                         "</services>");
    }

}
