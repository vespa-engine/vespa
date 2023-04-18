// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.serialization;

/**
 * Class used to represent up to 4 flags used in a DocumentUpdate.
 * These flags are stored as the 4 most significant bits in a 32 bit integer.
 *
 * Flags currently used:
 *   0) create-if-non-existent.
 *
 * @author geirst
 */
public class DocumentUpdateFlags {
    private byte flags;
    private DocumentUpdateFlags(byte flags) {
        this.flags = flags;
    }
    public DocumentUpdateFlags() {
        this.flags = 0;
    }
    public boolean getCreateIfNonExistent() {
        return (flags & 1) != 0;
    }
    public void setCreateIfNonExistent(boolean value) {
        flags &= ~1; // clear flag
        flags |= value ? (byte)1 : (byte)0; // set flag
    }
    public int injectInto(int value) {
        return extractValue(value) | (flags << 28);
    }
    public static DocumentUpdateFlags extractFlags(int combined) {
        return new DocumentUpdateFlags((byte)(combined >> 28));
    }
    public static int extractValue(int combined) {
        int mask = ~(~0 << 28);
        return combined & mask;
    }
}
