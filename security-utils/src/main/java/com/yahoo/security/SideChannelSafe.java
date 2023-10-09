// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.security;

import org.bouncycastle.util.Arrays;

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
     * @return true iff all bytes in the array are zero. An empty array always returns true
     *         to be in line with BouncyCastle semantics.
     */
    public static boolean allZeros(byte[] buf) {
        return Arrays.areAllZeroes(buf, 0, buf.length);
    }

    /**
     * Compare two byte arrays without the use of data-dependent branching that may leak information
     * about the contents of either of the arrays.
     *
     * <strong>Important:</strong> the <em>length</em> of the arrays is not considered secret, and
     * <em>may</em> be leaked if arrays of differing sizes are given.
     *
     * @param lhs first array of bytes to compare
     * @param rhs second array of bytes to compare
     * @return true iff both arrays have the same size and are element-wise identical
     */
    public static boolean arraysEqual(byte[] lhs, byte[] rhs) {
        return Arrays.constantTimeAreEqual(lhs, rhs);
    }

}
