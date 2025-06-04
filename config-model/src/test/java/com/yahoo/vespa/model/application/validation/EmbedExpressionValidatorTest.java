// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation;

import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.test.MockApplicationPackage;
import com.yahoo.vespa.model.VespaModel;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.util.List;

/**
 * @author bjorncs
 */
class EmbedExpressionValidatorTest {

    @Test
    void testNonExistingEmbedderCausesIllegalEntry() throws IOException, SAXException {
        // Create a schema that uses the 'embed' expression with a non-existing embedder ID
        var schema = """
                schema test {
                  field embedding type tensor(x[768]) {
                    indexing: input content | embed non_existing_embedder | attribute
                  }
                  document test {
                    field content type string {
                      indexing: summary | index
                    }
                  }
                }
                """;

        // Create a services.xml file that doesn't define the embedder component
        var services = """
                <?xml version="1.0" encoding="utf-8" ?>
                <services version="1.0">
                  <container id="default" version="1.0">
                    <search/>
                    <document-api/>
                  </container>
                  <content id="test" version="1.0">
                    <redundancy>1</redundancy>
                    <documents>
                      <document type="test" mode="index"/>
                    </documents>
                    <nodes count="1"/>
                  </content>
                </services>
                """;

        var deployState = new DeployState.Builder()
                .applicationPackage(new MockApplicationPackage.Builder()
                        .withServices(services)
                        .withSchemas(List.of(schema))
                        .build())
                .build();
        ValidationTester.expect(
                new EmbedExpressionValidator(),
                new VespaModel(deployState),
                deployState,
                "The 'embed' expression for field 'embedding' refers to an embedder with id 'non_existing_embedder'. No component with that id is configured.");
    }
}
