// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.schema.derived.TestableDeployLogger;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.content.utils.ApplicationPackageBuilder;
import com.yahoo.vespa.model.content.utils.ContentClusterBuilder;
import com.yahoo.vespa.model.content.utils.DocType;
import com.yahoo.vespa.model.content.utils.SchemaBuilder;
import com.yahoo.vespa.model.test.utils.VespaModelCreatorWithFilePkg;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author bjorncs
 */
public class StreamingValidatorTest {

    @Test
    void document_references_are_forbidden_in_streaming_search() {
        Throwable exception = assertThrows(IllegalArgumentException.class, () -> {
            new VespaModelCreatorWithFilePkg("src/test/cfg/application/validation/document_references_validation/")
                    .create();
        });
        assertTrue(exception.getMessage().contains("For streaming search cluster 'content.ad': Attribute 'campaign_ref' has type 'Reference<campaign>'. " +
                "Document references and imported fields are not allowed in streaming search."));
    }

    @Test
    void tensor_field_without_index_gives_no_warning() {
        var logger = new TestableDeployLogger();
        var model = createModel(logger, "field nn type tensor(x[2]) { indexing: attribute | summary\n" +
                    "attribute { distance-metric: euclidean } }");
        assertTrue(logger.warnings.isEmpty());
    }

    @Test
    void tensor_field_with_index_triggers_warning_in_streaming_search() {
        var logger = new TestableDeployLogger();
        var model = createModel(logger, "field nn type tensor(x[2]) { indexing: attribute | index | summary\n" +
                    "attribute { distance-metric: euclidean } }");
        assertEquals(1, logger.warnings.size());
        assertEquals("For streaming search cluster 'content.test', SD field 'nn': hnsw index is not relevant and not supported, ignoring setting",
                logger.warnings.get(0));
    }

    private static VespaModel createModel(DeployLogger logger, String sdContent) {
        var builder = new DeployState.Builder();
        builder.deployLogger(logger);
        return new ApplicationPackageBuilder()
                .addCluster(new ContentClusterBuilder().name("content").docTypes(List.of(DocType.streaming("test"))))
                .addSchemas(new SchemaBuilder().name("test").content(sdContent).build())
                .buildCreator().create(builder);
    }
}
