// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.rankingexpression.transform;

import com.yahoo.searchlib.rankingexpression.evaluation.Value;

import java.util.Map;

/**
 * Provides a context in which transforms on ranking expressions take place.
 *
 * @author bratseth
 */
public class TransformContext {

    private final Map<String, Value> constants;

    public TransformContext(Map<String, Value> constants) {
        this.constants = constants;
    }

    public Map<String, Value> constants() { return constants; }

}
