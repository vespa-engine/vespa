// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation;

import com.yahoo.vespa.model.test.utils.VespaModelCreatorWithFilePkg;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * @author Harald Musum
 */
public class NoPrefixForIndexesTest {

    @Test
    void requireThatPrefixIsSupported() {
        new VespaModelCreatorWithFilePkg("src/test/cfg/application/validation/prefix/").create();
    }

    @Test
    void requireThatPrefixIsSupportedForStreaming() {
        new VespaModelCreatorWithFilePkg("src/test/cfg/application/validation/prefix_streaming/").create();
    }

    @Test
    void requireThatPrefixIsIllegalForIndexField() {
        try {
            new VespaModelCreatorWithFilePkg("src/test/cfg/application/validation/prefix_index/").create();
            fail();
        } catch (IllegalArgumentException e) {
            assertEquals("For schema 'simple', field 'artist': match/index:prefix is not supported for indexes.", e.getMessage());
        }
    }

    @Test
    void requireThatPrefixIsIllegalForMixedAttributeAndIndexField() {
        try {
            new VespaModelCreatorWithFilePkg("src/test/cfg/application/validation/prefix_index_and_attribute/").create();
            fail();
        } catch (IllegalArgumentException e) {
            assertEquals("For schema 'simple', field 'artist': match/index:prefix is not supported for indexes.", e.getMessage());
        }
    }
}
