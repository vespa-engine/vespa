// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config;

/**
 * Superclass for all leaf nodes in a {@link ConfigInstance}.
 * <p>
 * Subclasses represent leaf nodes with different types. These
 * implementations should implement method value() with return-value
 * corresponding to the actual type.
 *
 */
public abstract class LeafNode<T> extends Node implements Cloneable {

    protected boolean initialized;
    protected T value;

    /**
     * Creates a new, uninitialized LeafNode
     */
    protected LeafNode() {
        initialized = false;
    }

    /**
     * Creates a new LeafNode.
     *
     * @param initialized true if this node is initialized.
     */
    protected LeafNode(boolean initialized) {
        this.initialized = initialized;
    }

    public T value() {
        return value;
    }

    /**
     * Subclasses must implement this, in compliance with the rules given in the return tag.
     *
     * @return the String representation of the node value, or the string "(null)" if the value is null.
     */
    public abstract String toString();

    /**
     * Subclasses must implement this, in compliance with the rules given in the return tag.
     *
     * @return the String representation of the node value, or the 'null' object if the node value is null.
     */
    public abstract String getValue();

    /**
     * Sets the value based on a string representation. Returns false if the value could
     * not be set from the given string.
     * TODO: return void (see doSetValue)
     *
     * @param value the value to set
     * @return true on success, false otherwise
     * @throws IllegalArgumentException when value is null
     */
    protected final boolean setValue(String value) {
        if (value == null)
            throw new IllegalArgumentException("Null value is not allowed");
        return doSetValue(value);
    }

    // TODO: should throw exception instead of return false.
    protected abstract boolean doSetValue(String value);

    /**
     * This method is meant for internal use in the configuration
     * system. Overrides Object.clone().
     *
     * @return a new instance similar to this object.
     */
    @Override
    protected LeafNode<?> clone() {
        try {
            return (LeafNode<?>) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new ConfigurationRuntimeException(e);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (! (o instanceof LeafNode<?> other)) return false;

        return value == null ? other.value == null :  value().equals(other.value);
    }

    @Override
    public int hashCode() {
        return (value == null) ? 0 : value.hashCode();
    }

    void serialize(String name, Serializer serializer) {
        serializer.serialize(name, getValue());
    }

    void serialize(Serializer serializer) {
        serializer.serialize(getValue());
    }

}
