package com.yahoo.searchlib.rankingexpression.evaluation;// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

import com.yahoo.tensor.TensorType;
import com.yahoo.tensor.evaluation.TypeContext;

import java.util.HashMap;
import java.util.Map;

/**
 * A context which only contains type information.
 *
 * @author bratseth
 */
public class TypeMapContext implements TypeContext {

    private final Map<String, TensorType> featureTypes = new HashMap<>();

    public void setType(String name, TensorType type) {
        featureTypes.put(name, type);
    }

    @Override
    public TensorType getType(String name) {
        return featureTypes.get(name);
    }

}
