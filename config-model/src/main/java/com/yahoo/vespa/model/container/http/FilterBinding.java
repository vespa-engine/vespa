// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.http;

import com.yahoo.component.ComponentSpecification;
import com.yahoo.vespa.model.container.component.BindingPattern;

import java.util.Objects;

/**
 * @author bjorncs
 */
public class FilterBinding {

    public enum Type {REQUEST, RESPONSE}

    private final Type type;
    private final ComponentSpecification chainId;
    private final BindingPattern binding;

    private FilterBinding(Type type, ComponentSpecification chainId, BindingPattern binding) {
        this.type = type;
        this.chainId = chainId;
        this.binding = binding;
    }

    public static FilterBinding create(Type type, ComponentSpecification chainId, BindingPattern binding) {
        return new FilterBinding(type, chainId, binding);
    }

    public ComponentSpecification chainId() {
        return chainId;
    }

    public BindingPattern binding() {
        return binding;
    }

    public Type type() { return type; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FilterBinding that = (FilterBinding) o;
        return type == that.type &&
                Objects.equals(chainId, that.chainId) &&
                Objects.equals(binding, that.binding);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, chainId, binding);
    }
}
