// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation;

import com.yahoo.vespa.model.test.utils.VespaModelCreatorWithFilePkg;
import org.junit.jupiter.api.Test;

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

}
