// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.component;

import com.yahoo.text.XML;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author ingrid
 */
class EmbedderBatchingConfigTest {

    @Test
    void parses_padding_buckets() {
        assertEquals(List.of(16, 32, 128),
                     EmbedderBatchingConfig.parsePaddingBuckets(
                             component("<batching max-size='8' max-delay='10ms' padding-buckets=' 16, 32 128 '/>")));
    }

    @Test
    void padding_buckets_are_empty_unless_configured() {
        assertEquals(List.of(), EmbedderBatchingConfig.parsePaddingBuckets(component("<batching max-size='8' max-delay='10ms'/>")));
        assertEquals(List.of(), EmbedderBatchingConfig.parsePaddingBuckets(component("")));
    }

    @Test
    void padding_buckets_may_be_in_any_order() {
        assertEquals(List.of(64, 16, 16),
                     EmbedderBatchingConfig.parsePaddingBuckets(
                             component("<batching max-size='8' max-delay='10ms' padding-buckets='64 16 16'/>")));
    }

    @Test
    void invalid_padding_buckets_are_rejected() {
        for (var buckets : List.of("16 x", "0 16", "16 -32")) {
            assertThrows(IllegalArgumentException.class,
                         () -> EmbedderBatchingConfig.parsePaddingBuckets(
                                 component("<batching max-size='8' max-delay='10ms' padding-buckets='" + buckets + "'/>")),
                         buckets);
        }
    }

    private static Element component(String children) {
        return XML.getDocument("<component id='test'>" + children + "</component>").getDocumentElement();
    }

}
