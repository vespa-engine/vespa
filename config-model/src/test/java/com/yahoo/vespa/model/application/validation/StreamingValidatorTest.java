// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation;

import com.yahoo.vespa.model.test.utils.VespaModelCreatorWithFilePkg;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

/**
 * @author bjorncs
 */
public class StreamingValidatorTest {
    @Rule
    public final ExpectedException exceptionRule = ExpectedException.none();

    @Test
    public void document_references_are_forbidden_in_streaming_search() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage(
                "For streaming search cluster 'content.ad': Attribute 'campaign_ref' has type 'Reference<campaign>'. " +
                        "Document references and imported fields are not allowed in streaming search.");
        new VespaModelCreatorWithFilePkg("src/test/cfg/application/validation/document_references_validation/")
                .create();
    }
}
