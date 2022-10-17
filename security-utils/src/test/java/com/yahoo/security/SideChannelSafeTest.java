// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * _Functional_ test of side channel safe utility functions. Testing that they're actually
 * (probably) side channel safe would be too flaky since it's inherently timing-dependent.
 */
public class SideChannelSafeTest {

    @Test
    void all_zeros_checks_length_and_array_contents() {
        assertFalse(SideChannelSafe.allZeros(new byte[0]));
        assertFalse(SideChannelSafe.allZeros(new byte[]{ 1 }));
        assertTrue(SideChannelSafe.allZeros(new byte[]{ 0 }));
        assertFalse(SideChannelSafe.allZeros(new byte[]{ 0, 0, 127, 0 }));
        assertFalse(SideChannelSafe.allZeros(new byte[]{ 0, 0, -1, 0 }));
        assertTrue(SideChannelSafe.allZeros(new byte[]{ 0, 0, 0 }));
    }

    @Test
    void arrays_equal_checks_length_and_array_contents() {
        assertTrue(SideChannelSafe.arraysEqual(new byte[0], new byte[0]));
        assertFalse(SideChannelSafe.arraysEqual(new byte[] { 0 }, new byte[0]));
        assertFalse(SideChannelSafe.arraysEqual(new byte[0], new byte[]{ 0 }));
        assertTrue(SideChannelSafe.arraysEqual(new byte[] { 0, 0, 0 }, new byte[] { 0, 0, 0 }));
        assertTrue(SideChannelSafe.arraysEqual(new byte[] { 0x7, 0xe }, new byte[] { 0x7, 0xe }));
        assertFalse(SideChannelSafe.arraysEqual(new byte[] { 0xe, 0x7 }, new byte[] { 0x7, 0xe }));
        assertFalse(SideChannelSafe.arraysEqual(new byte[] { -1, 127 }, new byte[] { 127, -1 }));
        assertFalse(SideChannelSafe.arraysEqual(new byte[] { -1, -1, 1 }, new byte[] { -1, -1, 2 }));
        assertFalse(SideChannelSafe.arraysEqual(new byte[] { 0, -1, 1 }, new byte[] { 0, -1, 3 }));
    }

}
