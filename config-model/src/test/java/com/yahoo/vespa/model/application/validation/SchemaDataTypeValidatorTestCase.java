// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * @author Simon Thoresen Hult
 */
public class SchemaDataTypeValidatorTestCase {

    @Test
    void requireThatSupportedTypesAreValidated() {
        new VespaModelCreatorWithFilePkg("src/test/cfg/application/validation/search_alltypes/").create();
    }

    @Test
    void requireThatStructsAreLegalInSearchClusters() {
        new VespaModelCreatorWithFilePkg("src/test/cfg/application/validation/search_struct/").create();
    }

    @Test
    void requireThatEmptyContentFieldIsLegalInSearchClusters() {
        new VespaModelCreatorWithFilePkg("src/test/cfg/application/validation/search_empty_content/").create();
    }

    @Test
    void requireThatIndexingMapsInNonStreamingClusterIsIllegal() {
        try {
            new VespaModelCreatorWithFilePkg("src/test/cfg/application/validation/index_struct/").create();
            fail();
        } catch (IllegalArgumentException e) {
            assertEquals("Field type 'Map<string,string>' cannot be indexed for search clusters (field 'baz' in definition " +
                    "'simple' for cluster 'content').", e.getMessage());
        }
    }

    @Test
    void indexedUriFieldIsOk() {
        var logger = new TestableDeployLogger();
        var model = createModel(logger, "field URI type uri { indexing: index | summary }");
        assertArrayEquals(new String[] {}, filter(logger.warnings).toArray());
    }

    @Test
    void indexedArrayOfUriFieldIsOk() {
        var logger = new TestableDeployLogger();
        var model = createModel(logger, "field URI type array<uri> { indexing: index | summary }");
        assertArrayEquals(new String[] {}, filter(logger.warnings).toArray());
    }

    @Test
    void indexedWeightedSetOfUriFieldIsOk() {
        var logger = new TestableDeployLogger();
        var model = createModel(logger, "field URI type weightedset<uri> { indexing: index | summary }");
        assertArrayEquals(new String[] {}, filter(logger.warnings).toArray());
    }

    @Test
    void indexedStringFieldIsOk() {
        var logger = new TestableDeployLogger();
        var model = createModel(logger, "field text type string { indexing: index | summary }");
        assertArrayEquals(new String[] {}, filter(logger.warnings).toArray());
    }

    @Test
    void indexedArrayOfStringFieldIsOk() {
        var logger = new TestableDeployLogger();
        var model = createModel(logger, "field text type array<string> { indexing: index | summary }");
        assertArrayEquals(new String[] {}, filter(logger.warnings).toArray());
    }

    @Test
    void indexedWeightedSetOfSTringFieldIsOk() {
        var logger = new TestableDeployLogger();
        var model = createModel(logger, "field text type weightedset<string> { indexing: index | summary }");
        assertArrayEquals(new String[] {}, filter(logger.warnings).toArray());
    }

    @Test
    void indexedTensorFieldIsOk() {
        var logger = new TestableDeployLogger();
        var model = createModel(logger, "field embedding type tensor(x[2]) { indexing: index | attribute | summary }");
        assertArrayEquals(new String[] {}, filter(logger.warnings).toArray());
    }

    @Test
    void indexedArrayOfArrayOfStringIsNotOk() {
        var logger = new TestableDeployLogger();
        var model = createModel(logger, "field text type array<array<string>> { indexing: index | summary }");
        assertArrayEquals(new String[] {"In cluster 'content', schema 'test', field 'text': Field type 'Array<Array<string>>' cannot be indexed"},
                filter(logger.warnings).toArray());
    }

    private static List<String> filter(List<String> warnings) {
        // filter out warnings about vespa-verify-ranksetup-bin
        return warnings.stream().filter(x -> x.indexOf("Cannot run program") == -1).toList();
    }

    private static VespaModel createModel(DeployLogger logger, String sdContent) {
        var builder = new DeployState.Builder();
        builder.deployLogger(logger);
        return new ApplicationPackageBuilder()
                .addCluster(new ContentClusterBuilder().name("content").docTypes(List.of(DocType.index("test"))))
                .addSchemas(new SchemaBuilder().name("test").content(sdContent).build())
                .buildCreator().create(builder);
    }

}
