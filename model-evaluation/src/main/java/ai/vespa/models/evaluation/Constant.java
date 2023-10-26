// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.models.evaluation;

import com.yahoo.tensor.Tensor;

/**
 * A named constant loaded from a file.
 *
 * This is immutable.
 *
 * @author bratseth
 */
class Constant {

    private final String name;
    private final Tensor value;

    Constant(String name, Tensor value) {
        this.name = name;
        this.value = value;
    }

    public String name() { return name; }

    public Tensor value() { return value; }

}
