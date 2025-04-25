package com.yahoo.vespa.model.builder.xml.dom;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BinaryUnitTest {

    @Test
    void handlesValidInput() {
        assertEquals(1024, BinaryUnit.valueOf("1024"));
        assertEquals(1024, BinaryUnit.valueOf("1k"));
        assertEquals(1024, BinaryUnit.valueOf("1kB"));
        assertEquals(1024, BinaryUnit.valueOf("1kiB"));
        assertEquals(1024, BinaryUnit.valueOf("1kb"));
        assertEquals(1024, BinaryUnit.valueOf("1kib"));
        assertEquals(3 * 1024 * 1024, BinaryUnit.valueOf("3MiB"));
        assertEquals(9.5 * 1024 * 1024 * 1024, BinaryUnit.valueOf("9.5GiB"));
    }

    @Test
    void handlesInvalidInput() {
        var exception = assertThrows(IllegalArgumentException.class, () -> BinaryUnit.valueOf("10A"));
        assertEquals("Value '10A' does not match the pattern for binary unit", exception.getMessage());

        exception = assertThrows(IllegalArgumentException.class, () -> BinaryUnit.valueOf("-1MiB"));
        assertEquals("Value '-1MiB' does not match the pattern for binary unit", exception.getMessage());
    }

    @Test
    void handlesLeadingZeros() {
        assertEquals(1024, BinaryUnit.valueOf("0001kiB"));
    }

}
