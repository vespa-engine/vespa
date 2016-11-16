package com.yahoo.tensor.functions;

import com.yahoo.tensor.MapTensor;
import com.yahoo.tensor.Tensor;

/**
 * A function which returns a constant tensor.
 * 
 * @author bratseth
 */
public class ConstantTensor extends PrimitiveTensorFunction {

    private final Tensor constant;
    
    public ConstantTensor(String tensorString) {
        this.constant = MapTensor.from(tensorString);
    }
    
    public ConstantTensor(Tensor tensor) {
        this.constant = tensor;
    }
    
    @Override
    public PrimitiveTensorFunction toPrimitive() { return this; }

    @Override
    public String toString() { return constant.toString(); }

}
