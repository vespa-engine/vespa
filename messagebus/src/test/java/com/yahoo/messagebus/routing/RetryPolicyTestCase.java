// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus.routing;

import com.yahoo.messagebus.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Simon Thoresen Hult
 */
public class RetryPolicyTestCase {

    private static final double SMALL = 0.00000000000000000001;

    @Test
    void testSimpleRetryPolicy() {
        RetryTransientErrorsPolicy policy = new RetryTransientErrorsPolicy();
        assertEquals(0.0, policy.getRetryDelay(0), SMALL);
        assertEquals(0.0, policy.getRetryDelay(1), SMALL);
        for (int i = 2; i < 15; i++) {
            assertEquals(0.001 * (1 << (i - 1)), policy.getRetryDelay(i), SMALL);
        }
        assertEquals(10.0, policy.getRetryDelay(15), SMALL);
        assertEquals(10.0, policy.getRetryDelay(20), SMALL);
        for (int j = ErrorCode.NONE; j < ErrorCode.ERROR_LIMIT; ++j) {
            policy.setEnabled(true);
            if (j < ErrorCode.FATAL_ERROR) {
                assertTrue(policy.canRetry(j));
            } else {
                assertFalse(policy.canRetry(j));
            }
            policy.setEnabled(false);
            assertFalse(policy.canRetry(j));
        }
    }

}
