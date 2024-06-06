package ai.vespa.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author bjorncs
 */
class BytesQuantityTest {

    @Test
    void from_string() {
        assertEquals(0L, BytesQuantity.fromString("0 bytes").toBytes());
        assertEquals(1L, BytesQuantity.fromString("1 byte").toBytes());
        assertEquals(10L, BytesQuantity.fromString("10 bytes").toBytes());
        assertEquals(1L, BytesQuantity.fromString("1 B").toBytes());
        assertEquals(1L, BytesQuantity.fromString("1B").toBytes());
        assertEquals(1L, BytesQuantity.fromString("1").toBytes());
        assertEquals(1024L, BytesQuantity.fromString("1kB").toBytes());
        assertEquals(1024L, BytesQuantity.fromString("1k").toBytes());
        assertEquals(1024L, BytesQuantity.fromString("1K").toBytes());
        assertEquals(1024L, BytesQuantity.fromString("1KB").toBytes());
        assertEquals(1024L * 1024, BytesQuantity.fromString("1MB").toBytes());
        assertEquals(1024L * 1024, BytesQuantity.fromString("1M").toBytes());
        assertEquals(1024L * 1024 * 1024, BytesQuantity.fromString("1GB").toBytes());
        assertEquals(1024L * 1024 * 1024, BytesQuantity.fromString("1G").toBytes());
        assertEquals(1024L * 1024 * 1024 * 1024, BytesQuantity.fromString("1TB").toBytes());
        assertEquals(1024L * 1024 * 1024 * 1024, BytesQuantity.fromString("1T").toBytes());
    }

    @Test
    void as_pretty_string() {
        assertEquals("0 bytes", BytesQuantity.ofBytes(0).asPrettyString());
        assertEquals("1 byte", BytesQuantity.ofBytes(1).asPrettyString());
        assertEquals("10 bytes", BytesQuantity.ofBytes(10).asPrettyString());
        assertEquals("1 kB", BytesQuantity.ofBytes(1024).asPrettyString());
        assertEquals("2 kB", BytesQuantity.ofKB(2).asPrettyString());
        assertEquals("3 MB", BytesQuantity.ofMB(3).asPrettyString());
        assertEquals("4 GB", BytesQuantity.ofGB(4).asPrettyString());
        assertEquals("5 TB", BytesQuantity.ofTB(5).asPrettyString());

        assertEquals("2560 bytes", BytesQuantity.ofBytes(2048 + 512).asPrettyString());
        assertEquals("3073 kB", BytesQuantity.ofBytes(3 * 1024 * 1024 + 1024).asPrettyString());
        assertEquals("5120 TB", BytesQuantity.ofBytes(1024L * 1024 * 1024 * 1024 * 1024 * 5).asPrettyString());
    }
}
