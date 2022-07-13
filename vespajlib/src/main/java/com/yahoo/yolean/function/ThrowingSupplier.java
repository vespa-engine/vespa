// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.yolean.function;

/**
 * Functional interface that mirrors the Supplier interface, but allows for an
 * exception to be thrown.
 *
 * @author oyving
 */
@FunctionalInterface
public interface ThrowingSupplier<R, E extends Throwable> {
    R get() throws E;
}
