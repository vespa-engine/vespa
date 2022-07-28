// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * @author bakksjo
 */
public class ReferencedResourceTestCase {
    @Test
    void requireThatGettersMatchConstructor() {
        final SharedResource resource = mock(SharedResource.class);
        final ResourceReference reference = mock(ResourceReference.class);
        final ReferencedResource<SharedResource> referencedResource = new ReferencedResource<>(resource, reference);
        assertSame(resource, referencedResource.getResource());
        assertSame(reference, referencedResource.getReference());
    }

    @Test
    void requireThatCloseCallsReferenceClose() {
        final SharedResource resource = mock(SharedResource.class);
        final ResourceReference reference = mock(ResourceReference.class);
        final ReferencedResource<SharedResource> referencedResource = new ReferencedResource<>(resource, reference);
        referencedResource.close();
        verify(reference, times(1)).close();
    }
}
