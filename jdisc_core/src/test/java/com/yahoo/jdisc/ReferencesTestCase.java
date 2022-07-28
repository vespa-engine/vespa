// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc;

import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * @author bakksjo
 */
public class ReferencesTestCase {
    @Test
    void requireThatFromResourceCallsReleaseOnResource() {
        final SharedResource resource = mock(SharedResource.class);
        final ResourceReference reference = References.fromResource(resource);
        reference.close();
        verify(resource, times(1)).release();
    }

    @Test
    void requireThatNoopReferenceCanBeCalledMultipleTimes() {
        References.NOOP_REFERENCE.close();
        References.NOOP_REFERENCE.close();
    }
}
