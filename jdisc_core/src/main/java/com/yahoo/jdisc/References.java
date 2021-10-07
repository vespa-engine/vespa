// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc;

/**
 * Utility class for working with {@link SharedResource}s and {@link ResourceReference}s.
 *
 * @author bakksjo
 */
public class References {
    // Prevents instantiation.
    private References() {
    }

    /**
     * A {@link ResourceReference} that does nothing.
     * Useful for e.g. testing of resource types when reference counting is not the focus.
     */
    public static final ResourceReference NOOP_REFERENCE = new ResourceReference() {
        @Override
        public void close() {
        }
    };

    /**
     * <p>Returns a {@link ResourceReference} that invokes {@link SharedResource#release()} on
     * {@link ResourceReference#close() close}. Useful for treating the "main" reference of a {@link SharedResource}
     * just as any other reference obtained by calling {@link SharedResource#refer()}. Example:</p>
     * <pre>
     *     final Request request = new Request(...);
     *     try (final ResourceReference ref = References.fromResource(request)) {
     *         ....
     *     }
     *     // The request will be released on exit from the try block.
     * </pre>
     *
     * @param resource The resource to create a ResourceReference for.
     * @return a ResourceReference whose close() method will call release() on the given resource.
     */
    public static ResourceReference fromResource(final SharedResource resource) {
        return new ResourceReference() {
            @Override
            public void close() {
                resource.release();
            }
        };
    }
}
