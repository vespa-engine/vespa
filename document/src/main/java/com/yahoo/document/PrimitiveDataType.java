// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document;

import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.vespa.objects.Ids;
import com.yahoo.vespa.objects.ObjectVisitor;

import java.util.Objects;

/**
 * @author Einar M R Rosenvinge
 */
public class PrimitiveDataType extends DataType {

    public static abstract class Factory {
        public abstract FieldValue create();
        public abstract FieldValue create(String value);
    }

    // The global class identifier shared with C++.
    public static final int classId = registerClass(Ids.document + 51, PrimitiveDataType.class);
    private final Class<? extends FieldValue> valueClass;
    private final Factory factory;

    /**
     * Creates a datatype
     *
     * @param name     the name of the type
     * @param code     the code (id) of the type
     * @param factory  the factory for creating field values of this type
     */
    protected PrimitiveDataType(java.lang.String name, int code, Class<? extends FieldValue> valueClass, Factory factory) {
        super(name, code);
        Objects.requireNonNull(valueClass, "valueClass");
        Objects.requireNonNull(factory, "factory");
        this.valueClass = valueClass;
        this.factory = factory;
    }

    @Override
    public PrimitiveDataType clone() {
        return (PrimitiveDataType)super.clone();
    }

    @Override
    public FieldValue createFieldValue() {
        return factory.create();
    }
    @Override
    public FieldValue createFieldValue(Object arg) {
        if (arg == null) return factory.create();
        if (arg instanceof String) return factory.create((String)arg);
        return super.createFieldValue(arg);
    }

    @Override
    public Class<? extends FieldValue> getValueClass() {
        return valueClass;
    }

    @Override
    public boolean isValueCompatible(FieldValue value) {
        return value != null && valueClass.isAssignableFrom(value.getClass());
    }

    @Override
    public PrimitiveDataType getPrimitiveType() {
        return this;
    }

    @Override
    public void visitMembers(ObjectVisitor visitor) {
        super.visitMembers(visitor);
        visitor.visit("valueclass", valueClass.getName());
    }
}
