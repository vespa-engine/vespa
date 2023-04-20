// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.security;

/**
 * Utility functions for comparing the contents of arrays without leaking information about the
 * data contained within them via timing side-channels. This is done by avoiding any branches
 * that depend on the array elements themselves. This inherently means that all operations have
 * both an upper and a lower bound in processing time that is O(n) for an array of size n, as there
 * can be no early exits.
 *
 * @author vekterli
 */
public class SideChannelSafe {

    /**
     * @return true iff all bytes in the array are zero. An empty array always returns false
     *         since it technically can't contain any zeros at all.
     */
    public static boolean allZeros(byte[] buf) {
        if (buf.length == 0) {
            return false;
        }
        byte accu = 0;
        for (byte b : buf) {
            accu |= b;
        }
        return (accu == 0);
    }

    /**
     * Compare two byte arrays without the use of data-dependent branching that may leak information
     * about the contents of either of the arrays.
     *
     * <strong>Important:</strong> the <em>length</em> of the arrays is not considered secret, and
     * will be leaked if arrays of differing sizes are given.
     *
     * @param lhs first array of bytes to compare
     * @param rhs second array of bytes to compare
     * @return true iff both arrays have the same size and are element-wise identical
     */
    public static boolean arraysEqual(byte[] lhs, byte[] rhs) {
        if (lhs.length != rhs.length) {
            return false;
        }
        // Only use constant time bitwise ops. `accu` will be non-zero if at least one bit
        // differed in any byte compared between the two arrays.
        byte accu = 0;
        for (int i = 0; i < lhs.length; ++i) {
            accu |= (byte)(lhs[i] ^ rhs[i]);
        }
        return (accu == 0);
    }

}
