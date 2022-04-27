// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.profile.types;

import com.yahoo.processing.request.Properties;
import com.yahoo.search.schema.internal.TensorConverter;
import com.yahoo.search.query.profile.QueryProfileRegistry;
import com.yahoo.search.query.profile.SubstituteString;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;

import java.util.Map;

/**
 * A tensor field type in a query profile
 *
 * @author bratseth
 */
public class TensorFieldType extends FieldType {

    private final TensorType type;

    /** Creates a tensor field type with information about the kind of tensor this will hold */
    public TensorFieldType(TensorType type) {
        this.type = type;
    }

    /** Returns information about the type of tensor this will hold */
    @Override
    public TensorType asTensorType() { return type; }

    @Override
    public Class getValueClass() { return Tensor.class; }

    @Override
    public String stringValue() { return type.toString(); }

    @Override
    public String toString() { return "field type " + stringValue(); }

    @Override
    public String toInstanceDescription() { return "a tensor"; }

    @Override
    public Object convertFrom(Object o, QueryProfileRegistry registry) {
        return convertFrom(o, ConversionContext.empty());
    }

    @Override
    public Object convertFrom(Object o, ConversionContext context) {
        if (o instanceof SubstituteString) return new SubstituteStringTensor((SubstituteString) o, type);
        return new TensorConverter(context.embedders()).convertTo(type, context.destination(), o, context.language());
    }

    public static TensorFieldType fromTypeString(String s) {
        return new TensorFieldType(TensorType.fromSpec(s));
    }

    /**
     * A substitute string that should become a tensor once the substitution is performed at lookup time.
     * This is to support substitution strings in tensor values by parsing (only) such tensors at
     * lookup time rather than at construction time.
     */
    private static class SubstituteStringTensor extends SubstituteString {

        private final TensorType type;

        SubstituteStringTensor(SubstituteString string, TensorType type) {
            super(string.components(), string.stringValue());
            this.type = type;
        }

        @Override
        public Object substitute(Map<String, String> context, Properties substitution) {
            String substituted = super.substitute(context, substitution).toString();
            return Tensor.from(type, substituted);
        }

    }

}
