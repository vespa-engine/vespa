// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.tensor.functions;

import com.yahoo.tensor.evaluation.Name;
import com.yahoo.tensor.evaluation.TypeContext;

import java.util.Optional;

/**
 * A context which is passed down to all nested functions when returning a string representation.
 *
 * @author bratseth
 */
public interface ToStringContext<NAMETYPE extends Name> {

    static <NAMETYPE extends Name> ToStringContext<NAMETYPE> empty() { return new EmptyStringContext<>(); }

    /** Returns the name an identifier is bound to, or null if not bound in this context */
    String getBinding(String name);

    /**
     * Returns the context used to resolve types in this, if present.
     * In some functions serialization depends on type information.
     */
    default Optional<TypeContext<NAMETYPE>> typeContext() { return Optional.empty(); }

    /**
     * Returns the parent context of this (the context we're in scope of when this is created),
     * or null if this is the root.
     */
    ToStringContext<NAMETYPE> parent();

    class EmptyStringContext<NAMETYPE extends Name> implements ToStringContext<NAMETYPE> {

        @Override
        public String getBinding(String name) { return null; }

        @Override
        public ToStringContext<NAMETYPE> parent() { return null; }

    }

}
