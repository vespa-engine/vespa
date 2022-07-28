// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.task.util.file;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author freva
 */
public class DiskSizeTest {

    @Test
    void bytes_to_display_count_test() {
        assertEquals("-1 bytes", DiskSize.of(-1).asString());
        assertEquals("123 bytes", DiskSize.of(123).asString());
        assertEquals("1 kB", DiskSize.of(1_000).asString());
        assertEquals("15 MB", DiskSize.of(15_000_000).asString());
        assertEquals("123 GB", DiskSize.of(123_456_789_012L).asString());
        assertEquals("988 TB", DiskSize.of(987_654_321_098_765L).asString());
        assertEquals("987.7 TB", DiskSize.of(987_654_321_098_765L).asString(1));
        assertEquals("987.65 TB", DiskSize.of(987_654_321_098_765L).asString(2));
        assertEquals("2 PB", DiskSize.of(2_000_000_000_000_000L).asString());
        assertEquals("9 EB", DiskSize.of(Long.MAX_VALUE).asString());
    }
}
