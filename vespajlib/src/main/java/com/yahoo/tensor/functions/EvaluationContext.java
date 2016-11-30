package com.yahoo.tensor.functions;

/**
 * An evaluation context which is passed down to all nested functions during evaluation.
 * The default implementation is empty as this library does not in itself have any need for a
 * context.
 * 
 * @author bratseth
 */
public interface EvaluationContext {
    
    static EvaluationContext empty() { return new EvaluationContext() {}; }
    
}
