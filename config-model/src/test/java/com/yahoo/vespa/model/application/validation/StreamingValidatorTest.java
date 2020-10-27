// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation;

import com.yahoo.vespa.model.test.utils.VespaModelCreatorWithFilePkg;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

/**
 * @author bjorncs
 */
public class StreamingValidatorTest {
    @Test
    public void document_references_are_forbidden_in_streaming_search() {
        Exception e = assertThrows(IllegalArgumentException.class,
                                   () -> new VespaModelCreatorWithFilePkg("src/test/cfg/application/validation/document_references_validation/").create());
        assertEquals("For streaming search cluster 'content.ad': Attribute 'campaign_ref' has type 'Reference<campaign>'. " +
                     "Document references and imported fields are not allowed in streaming search.",
                     e.getMessage());
    }
}
