package com.yahoo.searchlib.rankingexpression.integration.tensorflow;

import com.yahoo.tensor.Tensor;

/**
 * A tensor with a name
 *
 * @author bratseth
 */
public class NamedTensor {

    private final String name;
    private final Tensor tensor;

    public NamedTensor(String name, Tensor tensor) {
        this.name = name;
        this.tensor = tensor;
    }

    public String name() { return name; }
    public Tensor tensor() { return tensor; }

}
