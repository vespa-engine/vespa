// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.component;

import com.yahoo.text.XML;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author ingridhabrekke
 * @author glebashnik
 */
class EmbedderBatchingConfigTest {

    @Test
    void parses_padding_buckets() {
        assertEquals(List.of(16, 32, 128),
                     batching("<batching max-size='8' max-delay='10ms' padding-buckets=' 16  32\t128 '/>").paddingBuckets());
    }

    @Test
    void padding_buckets_are_empty_unless_configured() {
        assertEquals(List.of(), batching("<batching max-size='8' max-delay='10ms'/>").paddingBuckets());
        assertNull(batching(""));
    }

    @Test
    void padding_buckets_are_kept_in_the_configured_order() {
        assertEquals(List.of(64, 16, 16),
                     batching("<batching max-size='8' max-delay='10ms' padding-buckets='64 16 16'/>").paddingBuckets());
    }

    @Test
    void invalid_padding_buckets_are_rejected() {
        for (var buckets : List.of("16 x", "0 16", "16 -32", "16,32")) {
            assertThrows(IllegalArgumentException.class,
                         () -> batching("<batching max-size='8' max-delay='10ms' padding-buckets='" + buckets + "'/>"),
                         buckets);
        }
    }

    private static EmbedderBatchingConfig batching(String children) {
        Element component = XML.getDocument("<component id='test'>" + children + "</component>").getDocumentElement();
        return EmbedderBatchingConfig.parseBatchingElement(component);
    }

}
