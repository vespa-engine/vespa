// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.fieldoperation;

import com.yahoo.searchdefinition.document.SDField;

/**
 * @author Einar M R Rosenvinge
 */
public class WeightOperation implements FieldOperation {

    private int weight;

    public int getWeight() {
        return weight;
    }

    public void setWeight(int weight) {
        this.weight = weight;
    }

    public void apply(SDField field) {
        field.setWeight(weight);
    }

}
