// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition;

import com.yahoo.tensor.TensorType;
import com.yahoo.tensor.evaluation.TypeContext;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * A context which only contains type information.
 * This returns empty tensor types (double) for unknown features which are not
 * query, attribute or constant features, as we do not have information about which such
 * features exist (but we know those that exist are doubles).
 *
 * @author bratseth
 */
public class TypeMapContext implements TypeContext {

    private final Map<String, TensorType> featureTypes = new HashMap<>();

    public void setType(String name, TensorType type) {
        featureTypes.put(FeatureNames.canonicalize(name), type);
    }

    @Override
    public TensorType getType(String name) {
        if (FeatureNames.isConstantFeature(name) ||
            FeatureNames.isAttributeFeature(name) ||
            FeatureNames.isQueryFeature(name))
            return featureTypes.get(FeatureNames.canonicalize(name));
        else
            return TensorType.empty; // we do not have type information for these. Correct would be either empty or null
    }

}
