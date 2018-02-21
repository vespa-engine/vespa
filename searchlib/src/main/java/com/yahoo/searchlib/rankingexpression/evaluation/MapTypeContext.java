// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.rankingexpression.evaluation;// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

import com.yahoo.searchlib.rankingexpression.Reference;
import com.yahoo.tensor.TensorType;
import com.yahoo.tensor.evaluation.TypeContext;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * A context which only contains type information.
 *
 * @author bratseth
 */
public class MapTypeContext implements TypeContext<Reference> {

    private final Map<Reference, TensorType> featureTypes = new HashMap<>();

    public void setType(Reference reference, TensorType type) {
        featureTypes.put(reference, type);
    }

    @Override
    public TensorType getType(String reference) {
        throw new UnsupportedOperationException("Not able to parse gereral references from string form");
    }

    @Override
    public TensorType getType(Reference reference) {
        return featureTypes.get(reference);
    }

    /** Returns an unmodifiable map of the bindings in this */
    public Map<Reference, TensorType> bindings() { return Collections.unmodifiableMap(featureTypes); }

}
