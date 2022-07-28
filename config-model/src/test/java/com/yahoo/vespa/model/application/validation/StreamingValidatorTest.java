// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation;

import com.yahoo.vespa.model.test.utils.VespaModelCreatorWithFilePkg;
import org.junit.jupiter.api.Test;

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
}
