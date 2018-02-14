// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.tensor.evaluation;

import com.yahoo.tensor.TensorType;

/**
 * Provides type information about a context (set of variable bindings).
 *
 * @author bratseth
 */
public interface TypeContext {

    /**
     * Returns the type of the tensor with this name.
     *
     * @return returns the type of the tensor which will be returned by calling getTensor(name)
     *         or null if getTensor will return null.
     */
    TensorType getType(Name name);

    /** A name which is just a string. Names are value objects. */
    class Name {

        private final String name;

        public Name(String name) {
            this.name = name;
        }

        @Override
        public String toString() { return name; }

        @Override
        public int hashCode() { return name.hashCode(); }

        @Override
        public boolean equals(Object other) {
            if (other == this) return true;
            if ( ! (other instanceof Name)) return false;
            return ((Name)other).name.equals(this.name);
        }

    }


}
