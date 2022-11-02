// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.security;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author vekterli
 */
public class KeyIdTest {

    @Test
    void equality_predicated_on_key_id_byte_string() {
        var id0s = KeyId.ofString("");
        var id1s = KeyId.ofString("1");
        var id2s = KeyId.ofString("12");
        assertEquals(id0s, id0s);
        assertEquals(id1s, id1s);
        assertEquals(id2s, id2s);
        assertNotEquals(id0s, id1s);
        assertNotEquals(id1s, id0s);
        assertNotEquals(id1s, id2s);
        assertNotEquals(id0s, id2s);
        var id0b = KeyId.ofBytes(new byte[0]);
        var id1b = KeyId.ofBytes(new byte[]{ '1' });
        var id2b = KeyId.ofBytes(new byte[]{ '1', '2' });
        assertEquals(id0s, id0b);
        assertEquals(id1s, id1b);
        assertEquals(id2s, id2b);
    }

    @Test
    void accessors_return_expected_values() {
        byte[] fooBytes = new byte[]{'f','o','o'};
        byte[] barBytes = new byte[]{'b','a','r'};

        var id1 = KeyId.ofString("foo");
        assertEquals("foo", id1.asString());
        assertArrayEquals(fooBytes, id1.asBytes());

        var id2 = KeyId.ofBytes(barBytes);
        assertEquals("bar", id2.asString());
        assertArrayEquals(barBytes, id2.asBytes());
    }

    @Test
    void key_id_bytes_are_deep_copied_when_constructed_from_raw_byte_array() {
        byte[] keyBytes = new byte[]{'f','o','o'};
        byte[] expected = Arrays.copyOf(keyBytes, keyBytes.length);
        var id = KeyId.ofBytes(keyBytes);
        keyBytes[0] = 'b';
        assertArrayEquals(expected, id.asBytes());
    }

    @Test
    void can_construct_largest_possible_key_id() {
        byte[] okIdBytes = new byte[KeyId.MAX_KEY_ID_UTF8_LENGTH];
        Arrays.fill(okIdBytes, (byte)'A');
        var okId = KeyId.ofBytes(okIdBytes);
        assertArrayEquals(okIdBytes, okId.asBytes());
    }

    @Test
    void too_big_key_id_throws() {
        byte[] tooBigIdBytes = new byte[KeyId.MAX_KEY_ID_UTF8_LENGTH + 1];
        Arrays.fill(tooBigIdBytes, (byte)'A');
        assertThrows(IllegalArgumentException.class, () -> KeyId.ofBytes(tooBigIdBytes));
    }

    @Test
    void malformed_utf8_key_id_is_rejected_on_construction() {
        byte[] malformedIdBytes = new byte[]{ (byte)0xC0 }; // First part of a 2-byte continuation without trailing byte
        assertThrows(IllegalArgumentException.class, () -> KeyId.ofBytes(malformedIdBytes));
    }

}
