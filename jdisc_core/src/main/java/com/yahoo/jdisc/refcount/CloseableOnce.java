// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.refcount;

import com.yahoo.jdisc.ResourceReference;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Ensures that a ResourceReference can only be closed exactly once.
 *
 * @author baldersheim
 */
abstract class CloseableOnce implements ResourceReference {
        private final AtomicBoolean isReleased = new AtomicBoolean(false);

        @Override
        public final void close() {
            final boolean wasReleasedBefore = isReleased.getAndSet(true);
            if (wasReleasedBefore) {
                final String message = "Reference is already released and can only be released once."
                        + " State={ " + getReferences().currentState() + " }";
                throw new IllegalStateException(message);
            }
            onClose();
        }
        abstract void onClose();
        abstract References getReferences();
}
