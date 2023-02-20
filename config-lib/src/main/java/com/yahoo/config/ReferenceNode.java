// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config;

/**
 * A ReferenceNode class represents a reference (that is a config-id)
 * in a {@link ConfigInstance}.
 */
public class ReferenceNode extends LeafNode<String> {

    public ReferenceNode() {
    }

    /**
     * Creates a new ReferenceNode with the given value.
     *
     * @param value the value of this ReferenceNode
     */
    public ReferenceNode(String value) {
        super(true);
        this.value = stripQuotes(value);
    }

    /**
     * Returns the value of this reference node. Same as {@link
     * #toString()} since the value of a ReferenceNode is a String (but
     * implementations in other {@link LeafNode} sub-classes differ).
     *
     * @return the string representation of this ReferenceNode.
     */
    public String value() {
        return value;
    }

    @Override
    public String getValue() {
        return value();
    }

    @Override
    public String toString() {
        return (value == null) ? "(null)" : getValue();
    }

    @Override
    protected boolean doSetValue(String value) {
        this.value = stripQuotes(value);
        return true;
    }

    /**
     * Overrides {@link Node#postInitialize(String)}
     * Checks for ":parent:" values, which will be replaced by the configId.
     *
     * @param configId  the configId of the ConfigInstance that owns (or is) this node
     */
    @Override
    public void postInitialize(String configId) {
        super.postInitialize(configId);
        if (":parent:".equals(value()))  {
            doSetValue(configId);
        }
    }

    /**
     * Strips the quotes before or after the value, if present.
     */
    static String stripQuotes(String value) {
        if (value == null) {
            return value;
        }
        StringBuilder buffer = new StringBuilder(value.trim());
        if (buffer.length() > 0 && buffer.charAt(0) == '"') {
            buffer.deleteCharAt(0);
        }
        if (buffer.length() > 0 && buffer.charAt(buffer.length() - 1) == '"') {
            buffer.setLength(buffer.length() - 1);
        }
        return buffer.toString();
    }

}
