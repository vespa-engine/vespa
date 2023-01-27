// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.datatypes;

import com.yahoo.document.DataType;
import com.yahoo.document.Field;
import com.yahoo.document.PrimitiveDataType;
import com.yahoo.document.predicate.Predicate;
import com.yahoo.document.serialization.FieldReader;
import com.yahoo.document.serialization.FieldWriter;
import com.yahoo.document.serialization.XmlStream;

import java.util.Objects;

/**
 * @author Simon Thoresen Hult
 */
public class PredicateFieldValue extends FieldValue {

    private Predicate predicate;

    public PredicateFieldValue() {
        this((Predicate)null);
    }

    public PredicateFieldValue(Predicate predicate) {
        this.predicate = predicate;
    }

    public PredicateFieldValue(String predicateString) {
        this(Predicate.fromString(predicateString));
    }

    public Predicate getPredicate() {
        return predicate;
    }

    public PredicateFieldValue setPredicate(Predicate predicate) {
        this.predicate = predicate;
        return this;
    }

    @Override
    public DataType getDataType() {
        return DataType.PREDICATE;
    }

    @Override
    @Deprecated
    public void printXml(XmlStream xml) {
        if (predicate == null) {
            return;
        }
        xml.addContent(predicate.toString());
    }

    @Override
    public void clear() {
        predicate = null;
    }

    @Override
    public void assign(Object o) {
        if (o == null) {
            predicate = null;
        } else if (o instanceof Predicate) {
            predicate = (Predicate)o;
        } else if (o instanceof PredicateFieldValue) {
            predicate = ((PredicateFieldValue)o).predicate;
        } else {
            throw new IllegalArgumentException("Expected " + getClass().getName() + ", got " +
                                               o.getClass().getName() + ".");
        }
    }

    @Override
    public void serialize(Field field, FieldWriter writer) {
        writer.write(field, this);
    }

    @Override
    public void deserialize(Field field, FieldReader reader) {
        reader.read(field, this);
    }

    @Override
    public Object getWrappedValue() {
        return predicate;
    }

    @Override
    public PredicateFieldValue clone() {
        PredicateFieldValue obj = (PredicateFieldValue)super.clone();
        if (predicate != null) {
            try {
                obj.predicate = predicate.clone();
            } catch (CloneNotSupportedException e) {
                throw new AssertionError(e);
            }
        }
        return obj;
    }

    @Override
    public int hashCode() {
        return predicate != null ? predicate.hashCode() : 31;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (!(obj instanceof PredicateFieldValue)) {
            return false;
        }
        PredicateFieldValue rhs = (PredicateFieldValue)obj;
        if (!Objects.equals(predicate, rhs.predicate)) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return String.valueOf(predicate);
    }

    public static PrimitiveDataType.Factory getFactory() {
        return new Factory();
    }
    private static class Factory extends PrimitiveDataType.Factory {
        @Override public FieldValue create() { return new PredicateFieldValue(); }
        @Override public FieldValue create(String value) { return new PredicateFieldValue(value); }
    }
}
