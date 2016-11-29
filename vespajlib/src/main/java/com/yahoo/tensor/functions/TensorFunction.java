package com.yahoo.tensor.functions;

/**
 * A representation of a tensor function which is able to be translated to a set of primitive
 * tensor functions if necessary.
 * All tensor functions are immutable.
 * 
 * @author bratseth
 */
public abstract class TensorFunction {

    /**
     * Translate this function - and all of its arguments recursively -
     * to a tree of primitive functions only.
     *
     * @return a tree of primitive functions implementing this
     */
    public abstract PrimitiveTensorFunction toPrimitive();

}
