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
