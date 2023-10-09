// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.lang;

/**
 * This interface publicly exposes the clone method.
 * Implement this to allow faster clone.
 *
 * @author bratseth
 */
public interface PublicCloneable<T> extends Cloneable {
    T clone();
}
