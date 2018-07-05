// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation;

import com.yahoo.vespa.model.test.utils.VespaModelCreatorWithFilePkg;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * @author Harald Musum
 */
public class NoPrefixForIndexesTest {

    @Test
    public void requireThatPrefixIsSupported() {
        new VespaModelCreatorWithFilePkg("src/test/cfg/application/validation/prefix/").create();
    }

    @Test
    public void requireThatPrefixIsSupportedForStreaming() {
        new VespaModelCreatorWithFilePkg("src/test/cfg/application/validation/prefix_streaming/").create();
    }

    @Test
    public void requireThatPrefixIsIllegalForIndexField() {
        try {
            new VespaModelCreatorWithFilePkg("src/test/cfg/application/validation/prefix_index/").create();
            fail();
        } catch (IllegalArgumentException e) {
            assertEquals("For search 'simple', field 'artist': match/index:prefix is not supported for indexes.", e.getMessage());
        }
    }

    @Test
    public void requireThatPrefixIsIllegalForMixedAttributeAndIndexField() {
        try {
            new VespaModelCreatorWithFilePkg("src/test/cfg/application/validation/prefix_index_and_attribute/").create();
            fail();
        } catch (IllegalArgumentException e) {
            assertEquals("For search 'simple', field 'artist': match/index:prefix is not supported for indexes.", e.getMessage());
        }
    }
}
