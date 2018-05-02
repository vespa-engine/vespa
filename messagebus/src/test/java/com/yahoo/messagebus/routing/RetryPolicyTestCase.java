// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus.routing;

import com.yahoo.messagebus.ErrorCode;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author Simon Thoresen
 */
public class RetryPolicyTestCase {

    @Test
    public void testSimpleRetryPolicy() {
        RetryTransientErrorsPolicy policy = new RetryTransientErrorsPolicy();
        for (int i = 0; i < 5; ++i) {
            double delay = i / 3.0;
            policy.setBaseDelay(delay);
            for (int j = 0; j < 5; ++j) {
                assertEquals((int)(j * delay), (int)policy.getRetryDelay(j));
            }
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

}
