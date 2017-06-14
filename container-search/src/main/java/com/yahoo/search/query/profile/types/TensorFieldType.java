// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.profile.types;

import com.yahoo.search.query.profile.QueryProfileRegistry;
import com.yahoo.search.query.profile.compiled.CompiledQueryProfileRegistry;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;

import java.util.Optional;

/**
 * A tensor field type in a query profile
 *
 * @author bratseth
 */
public class TensorFieldType extends FieldType {

    // TODO: Require tensor type
    
    private final Optional<TensorType> type;

    /** Creates a tensor field type with optional information about the kind of tensor this will hold */
    public TensorFieldType(Optional<TensorType> type) {
        this.type = type;
    }

    /** Returns information about the type of tensor this will hold, or empty to allow any kind of tensor */
    public Optional<TensorType> type() { return type; }

    @Override
    public Class getValueClass() { return Tensor.class; }

    @Override
    public String stringValue() { return "tensor"; }

    @Override
    public String toString() { return "field type " + stringValue(); }

    @Override
    public String toInstanceDescription() { return "a tensor"; }

    @Override
    public Object convertFrom(Object o, QueryProfileRegistry registry) {
        if (o instanceof Tensor) return o;
        if (o instanceof String) return type.isPresent() ? Tensor.from(type.get(), (String)o) : Tensor.from((String)o);
        return null;
    }

    @Override
    public Object convertFrom(Object o, CompiledQueryProfileRegistry registry) {
        return convertFrom(o, (QueryProfileRegistry)null);
    }

    public static TensorFieldType fromTypeString(String s) {
        if (s.equals("tensor")) return genericTensorType;
        return new TensorFieldType(Optional.of(TensorType.fromSpec(s)));
    }


}
