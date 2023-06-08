// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author freva
 */
class CloudNameTest {

    @Test
    void returns_same_instance_for_known_clouds() {
        assertSame(CloudName.from("aws"), CloudName.AWS);
        assertSame(CloudName.from("gcp"), CloudName.GCP);
        assertSame(CloudName.from("default"), CloudName.DEFAULT);
        assertSame(CloudName.from("yahoo"), CloudName.YAHOO);
        assertThrows(IllegalArgumentException.class, () -> CloudName.from("aWs")); // Must be lower case
    }
}
