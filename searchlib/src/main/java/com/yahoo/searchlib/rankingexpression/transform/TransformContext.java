// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.rankingexpression.transform;

import com.yahoo.searchlib.rankingexpression.Reference;
import com.yahoo.searchlib.rankingexpression.evaluation.Value;
import com.yahoo.tensor.evaluation.TypeContext;

import java.util.Map;

/**
 * Provides a context in which transforms on ranking expressions take place.
 *
 * @author bratseth
 */
public class TransformContext {

    private final Map<String, Value> constants;
    private final TypeContext<Reference> types;

    public TransformContext(Map<String, Value> constants, TypeContext<Reference> types) {
        this.constants = constants;
        this.types = types;
    }

    public Map<String, Value> constants() { return constants; }

    /**
     * Returns the types known in this context. We may have type information for references
     * for which no value is available
     */
    public TypeContext<Reference> types() { return types; }

}
