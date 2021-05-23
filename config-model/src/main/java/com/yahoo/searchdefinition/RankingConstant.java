// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition;

import com.yahoo.tensor.TensorType;

/**
 * A global ranking constant distributed using file distribution.
 * Ranking constants must be sent to some services to be useful - this is done
 * by calling the sentTo method during the prepare phase of building models.
 *
 * @author arnej
 * @author bratseth
 */
public class RankingConstant extends DistributableResource {
    private TensorType tensorType = null;

    public RankingConstant(String name) {
        super(name);
    }

    public RankingConstant(String name, TensorType type, String fileName) {
        super(name, fileName);
        this.tensorType = type;
        validate();
    }

    public void setType(TensorType type) {
        this.tensorType = type;
    }

    public TensorType getTensorType() { return tensorType; }
    public String getType() { return tensorType.toString(); }

    public void validate() {
        super.validate();
        if (tensorType == null)
            throw new IllegalArgumentException("Ranking constant '" + getName() + "' must have a type.");
        if (tensorType.dimensions().stream().anyMatch(d -> d.isIndexed() && d.size().isEmpty()))
            throw new IllegalArgumentException("Illegal type in field " + getName() + " type " + tensorType +
                                               ": Dense tensor dimensions must have a size");
    }

    public String toString() {
        StringBuilder b = new StringBuilder(super.toString())
                .append("' of type '").append(tensorType).append("'");
        return b.toString();
    }

}
