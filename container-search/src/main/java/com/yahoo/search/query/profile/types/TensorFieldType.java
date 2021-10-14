// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.profile.types;

import com.yahoo.language.process.Embedder;
import com.yahoo.search.query.profile.QueryProfileRegistry;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;

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
        if (o instanceof Tensor) return o;
        if (o instanceof String && ((String)o).startsWith("embed(")) return encode((String)o, context);
        if (o instanceof String) return Tensor.from(type, (String)o);
        return null;
    }

    private Tensor encode(String s, ConversionContext context) {
        if ( ! s.endsWith(")"))
            throw new IllegalArgumentException("Expected any string enclosed in embed(), but the argument does not end by ')'");
        String text = s.substring("embed(".length(), s.length() - 1);
        return context.embedder().embed(text, toEmbedderContext(context), type);
    }

    private Embedder.Context toEmbedderContext(ConversionContext context) {
        return new Embedder.Context(context.destination()).setLanguage(context.language());
    }

    public static TensorFieldType fromTypeString(String s) {
        return new TensorFieldType(TensorType.fromSpec(s));
    }

}
