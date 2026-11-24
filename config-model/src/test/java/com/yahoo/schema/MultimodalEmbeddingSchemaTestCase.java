// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema;

import com.yahoo.config.model.deploy.TestDeployState;
import com.yahoo.config.model.test.MockApplicationPackage;
import com.yahoo.vespa.configdefinition.IlscriptsConfig;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.application.validation.EmbedExpressionValidator;
import com.yahoo.vespa.model.application.validation.ValidationTester;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests schemas which embed raw fields and parts: 'input text, input image | embed'.
 *
 * @author glebashnik
 */
class MultimodalEmbeddingSchemaTestCase {

    private static final String services = """
            <?xml version="1.0" encoding="utf-8" ?>
            <services version="1.0">
              <container id="default" version="1.0">
                <component id="myEmbedder" class="com.example.Embedder" bundle="example"/>
                <search/>
                <document-api/>
              </container>
              <content id="test" version="1.0">
                <redundancy>1</redundancy>
                <documents>
                  <document type="product" mode="index"/>
                </documents>
                <nodes count="1"/>
              </content>
            </services>
            """;

    @Test
    void testEmbedRawAndParts() throws Exception {
        var schema = """
                schema product {
                  document product {
                    field title type string {
                      indexing: summary | index
                    }
                    field description type string {
                      indexing: summary | index
                    }
                    field image type raw {}
                  }
                  field image_embedding type tensor<float>(x[768]) {
                    indexing: input image | embed myEmbedder | attribute | index
                  }
                  field product_embedding type tensor<float>(x[768]) {
                    indexing: input title . " " . input description, input image | embed myEmbedder | attribute | index
                  }
                }
                """;
        var script = String.join("\n", indexingScriptOf(schema));
        assertTrue(script.contains("input image | embed myEmbedder | attribute image_embedding | index image_embedding;"), script);
        assertTrue(script.contains("input title . \" \" . input description, input image | embed myEmbedder | " +
                                    "attribute product_embedding | index product_embedding;"), script);
    }

    @Test
    void testPartsWithoutEmbedIsRejected() {
        var schema = """
                schema product {
                  document product {
                    field title type string {
                      indexing: summary | index
                    }
                    field image type raw {}
                  }
                  field combined type string {
                    indexing: input title, input image | summary
                  }
                }
                """;
        var e = assertThrows(IllegalArgumentException.class, () -> indexingScriptOf(schema));
        assertTrue(e.getMessage().contains("parts must be directly followed by '| embed'"), e.getMessage());
    }

    @Test
    void testCommaInPlaceOfPipeIsRejected() {
        var schema = """
                schema product {
                  document product {
                    field title type string {
                      indexing: summary | index
                    }
                  }
                  field title_embedding type tensor<float>(x[768]) {
                    indexing: input title | embed myEmbedder | attribute, index
                  }
                }
                """;
        var e = assertThrows(IllegalArgumentException.class, () -> indexingScriptOf(schema));
        assertTrue(e.getMessage().contains("A comma separates parts, and parts must be directly followed by '| embed'"), e.getMessage());
    }

    @Test
    void testTextOnlyEmbedderOnlyDeploysWithSingleTextInput() {
        var services = MultimodalEmbeddingSchemaTestCase.services.replace(
                """
                <component id="myEmbedder" class="com.example.Embedder" bundle="example"/>
                """.strip(),
                """
                <component id="myEmbedder" type="hugging-face-embedder">
                  <transformer-model url="https://my/url/model.onnx"/>
                  <tokenizer-model url="https://my/url/tokenizer.json"/>
                </component>
                """.strip());
        var textSchema = """
                schema product {
                  document product {
                    field title type string {
                      indexing: summary | index
                    }
                    field image type raw {}
                  }
                  field title_embedding type tensor<float>(x[768]) {
                    indexing: input title | embed myEmbedder | attribute
                  }
                }
                """;
        indexingScriptOf(textSchema, services);

        var rawSchema = textSchema.replace("input title | embed myEmbedder", "input image | embed myEmbedder");
        var e = assertThrows(IllegalArgumentException.class, () -> indexingScriptOf(rawSchema, services));
        String rejection = "The 'embed' expression for field 'title_embedding' in schema 'product' uses the embedder " +
                           "with id 'myEmbedder', which accepts text, but is given image.";
        assertTrue(e.getMessage().contains(rejection), e.getMessage());
        assertEquals(e.getMessage().indexOf(rejection), e.getMessage().lastIndexOf(rejection),
                     "The rejection is reported once: " + e.getMessage());

        var multipartSchema = textSchema.replace("input title | embed myEmbedder", "input title, input image | embed myEmbedder");
        e = assertThrows(IllegalArgumentException.class, () -> indexingScriptOf(multipartSchema, services));
        assertTrue(e.getMessage().contains("uses the embedder with id 'myEmbedder', " +
                                           "which accepts text, but is given text and image."), e.getMessage());

        // An 'embed' with no embedder id uses the only embedder
        var rawSchemaWithNoId = textSchema.replace("input title | embed myEmbedder", "input image | embed");
        e = assertThrows(IllegalArgumentException.class, () -> indexingScriptOf(rawSchemaWithNoId, services));
        assertTrue(e.getMessage().contains("uses the embedder with id 'myEmbedder', " +
                                           "which accepts text, but is given image."), e.getMessage());
        indexingScriptOf(textSchema.replace("input title | embed myEmbedder", "input title | embed"), services);

        // Every 'embed' of a statement is validated, not only the last one
        var twoEmbedsSchema = textSchema.replace(
                "input title | embed myEmbedder",
                "(input image | embed myEmbedder) || (input title | embed myEmbedder)");
        e = assertThrows(IllegalArgumentException.class, () -> indexingScriptOf(twoEmbedsSchema, services));
        assertTrue(e.getMessage().contains("uses the embedder with id 'myEmbedder', " +
                                           "which accepts text, but is given image."), e.getMessage());
    }

    @Test
    void testPartsMustBeOneStringAndOneRaw() {
        var schema = """
                schema product {
                  document product {
                    field title type string {
                      indexing: summary | index
                    }
                    field description type string {
                      indexing: summary | index
                    }
                  }
                  field product_embedding type tensor<float>(x[768]) {
                    indexing: input title, input description | embed myEmbedder | attribute
                  }
                }
                """;
        var e = assertThrows(IllegalArgumentException.class, () -> indexingScriptOf(schema));
        assertTrue(e.getMessage().contains("The parts to embed must be one string and one raw, " +
                                           "but got 2 parts: [string, string]. " +
                                           "Use '.' to join strings into one part"), e.getMessage());
    }

    @Test
    void testTokenizerOnlyDeploysWithTextInput() {
        var services = MultimodalEmbeddingSchemaTestCase.services.replace(
                """
                <component id="myEmbedder" class="com.example.Embedder" bundle="example"/>
                """.strip(),
                """
                <component id="tokenizer" type="hugging-face-tokenizer">
                  <model url="https://my/url/tokenizer.json"/>
                </component>
                """.strip());
        var textSchema = """
                schema product {
                  document product {
                    field title type string {
                      indexing: summary | index
                    }
                    field image type raw {}
                  }
                  field title_tokens type tensor<float>(d0[128]) {
                    indexing: input title | embed tokenizer | attribute
                  }
                }
                """;
        indexingScriptOf(textSchema, services);

        var rawSchema = textSchema.replace("input title | embed tokenizer", "input image | embed tokenizer");
        var e = assertThrows(IllegalArgumentException.class, () -> indexingScriptOf(rawSchema, services));
        assertTrue(e.getMessage().contains("uses the embedder with id 'tokenizer', " +
                                           "which accepts text, but is given image."), e.getMessage());
    }

    @Test
    void testEmbedWithNoEmbedderIdDeploysWithNoContainerCluster() {
        String schema = """
                schema product {
                  document product {
                    field title type string {
                      indexing: summary | index
                    }
                  }
                  field title_embedding type tensor<float>(x[768]) {
                    indexing: input title | embed | attribute
                  }
                }
                """;
        String servicesWithNoContainerCluster = """
                <?xml version="1.0" encoding="utf-8" ?>
                <services version="1.0">
                  <content id="test" version="1.0">
                    <redundancy>1</redundancy>
                    <documents>
                      <document type="product" mode="index"/>
                    </documents>
                    <nodes count="1"/>
                  </content>
                </services>
                """;
        String script = String.join("\n", indexingScriptOf(schema, servicesWithNoContainerCluster));
        assertTrue(script.contains("input title | embed | attribute title_embedding;"), script);
    }

    private static List<String> indexingScriptOf(String schema) {
        return indexingScriptOf(schema, services);
    }

    private static List<String> indexingScriptOf(String schema, String services) {
        try {
            return indexingScriptOfThrowing(schema, services);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static List<String> indexingScriptOfThrowing(String schema, String services) throws Exception {
        var deployState = TestDeployState.createBuilder()
                                         .applicationPackage(new MockApplicationPackage.Builder()
                                                                     .withServices(services)
                                                                     .withSchemas(List.of(schema))
                                                                     .build())
                                         .build();
        var model = new VespaModel(deployState);
        ValidationTester.validate(new EmbedExpressionValidator(), model, deployState);
        var config = model.getConfig(IlscriptsConfig.class, "test/search/cluster.test");
        assertEquals(1, config.ilscript().size());
        return config.ilscript(0).content();
    }

}
