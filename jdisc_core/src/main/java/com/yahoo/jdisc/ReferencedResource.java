// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc;

/**
 * <p>Utility class for working with reference-counted {@link SharedResource}s.</p>
 *
 * <p>Sometimes, you may want a method to return <i>both</i> a resource object <i>and</i>
 * a {@link ResourceReference} that refers the resource object (for later release of the resource).
 * Java methods cannot return multiple objects, so this class provides Pair-like functionality
 * for returning both.</p>
 *
 * <p>Example usage:</p>
 * <pre>
 *     ReferencedResource&lt;MyResource&gt; getResource() {
 *         final ResourceReference ref = resource.refer();
 *         return new ReferencedResource(resource, ref);
 *     }
 *
 *     void useResource() {
 *         final ReferencedResource&lt;MyResource&gt; referencedResource = getResource();
 *         referencedResource.getResource().use();
 *         referencedResource.getReference().close();
 *     }
 * </pre>
 *
 * <p>This class implements AutoCloseable, so the latter method may also be written as follows:</p>
 * <pre>
 *     void useResource() {
 *         for (final ReferencedResource&lt;MyResource&gt; referencedResource = getResource()) {
 *             referencedResource.getResource().use();
 *         }
 *     }
 * </pre>
 *
 * @author bakksjo
 */
public class ReferencedResource<T extends SharedResource> implements AutoCloseable {
    private final T resource;
    private final ResourceReference reference;

    public ReferencedResource(final T resource, final ResourceReference reference) {
        this.resource = resource;
        this.reference = reference;
    }

    public T getResource() {
        return resource;
    }

    public ResourceReference getReference() {
        return reference;
    }

    @Override
    public void close() {
        reference.close();
    }
}
