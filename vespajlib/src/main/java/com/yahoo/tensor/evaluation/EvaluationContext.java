// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.tensor.evaluation;

import com.google.common.annotations.Beta;

import java.util.HashMap;

/**
 * An evaluation context which is passed down to all nested functions during evaluation.
 * The default context is empty to allow various evaluation frameworks to support their own implementation.
 * 
 * @author bratseth
 */
@Beta
public interface EvaluationContext {

}
