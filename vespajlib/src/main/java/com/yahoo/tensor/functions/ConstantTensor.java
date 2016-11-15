package com.yahoo.tensor.functions;

import com.yahoo.tensor.MapTensor;

/**
 * A function which returns a constant tensor.
 * 
 * @author bratseth
 */
public class ConstantTensor extends PrimitiveTensorFunction {

    private final MapTensor constant;
    
    public ConstantTensor(String tensorString) {
        this.constant = MapTensor.from(tensorString);
    }
    
    @Override
    public PrimitiveTensorFunction toPrimitive() { return this; }

    @Override
    public String toString() { return constant.toString(); }

}
