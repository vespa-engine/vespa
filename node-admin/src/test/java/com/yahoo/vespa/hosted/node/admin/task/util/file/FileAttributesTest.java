// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.task.util.file;

import org.junit.jupiter.api.Test;

import static com.yahoo.vespa.hosted.node.admin.task.util.file.FileAttributes.deviceMajor;
import static com.yahoo.vespa.hosted.node.admin.task.util.file.FileAttributes.deviceMinor;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author freva
 */
class FileAttributesTest {

    @Test
    void parse_dev_t() {
        assertEquals(0x12345BCD, deviceMajor(0x1234567890ABCDEFL));
        assertEquals(0x67890AEF, deviceMinor(0x1234567890ABCDEFL));
    }
}
