// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.predicate;

/**
 * @author Simon Thoresen Hult
 */
public class BooleanPredicate extends PredicateValue {

    private boolean value;

    public BooleanPredicate(boolean value) {
        this.value = value;
    }

    public boolean getValue() {
        return value;
    }

    public BooleanPredicate setValue(boolean value) {
        this.value = value;
        return this;
    }

    @Override
    public BooleanPredicate clone() throws CloneNotSupportedException {
        return (BooleanPredicate)super.clone();
    }

    @Override
    public int hashCode() {
        return value ? Boolean.TRUE.hashCode() : Boolean.FALSE.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (!(obj instanceof BooleanPredicate)) {
            return false;
        }
        BooleanPredicate rhs = (BooleanPredicate)obj;
        if (value != rhs.value) {
            return false;
        }
        return true;
    }

    @Override
    protected void appendTo(StringBuilder out) {
        out.append(value ? "true" : "false");
    }

}
