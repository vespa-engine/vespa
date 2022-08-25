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
