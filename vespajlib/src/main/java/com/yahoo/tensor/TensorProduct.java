// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.tensor;

import com.google.common.collect.ImmutableMap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;

/**
 * Computes a <i>sparse tensor product</i>, see {@link Tensor#multiply}
 *
 * @author bratseth
 */
class TensorProduct {

    private final Set<String> dimensionsA, dimensionsB;

    private final Set<String> dimensions;
    private final ImmutableMap.Builder<TensorAddress, Double> cells = new ImmutableMap.Builder<>();

    public TensorProduct(Tensor a, Tensor b) {
        dimensionsA = a.dimensions();
        dimensionsB = b.dimensions();

        // Dimension product
        dimensions = TensorOperations.combineDimensions(a, b);

        // Cell product (slow baseline implementation)
        for (Map.Entry<TensorAddress, Double> aCell : a.cells().entrySet()) {
            for (Map.Entry<TensorAddress, Double> bCell : b.cells().entrySet()) {
                TensorAddress combinedAddress = combine(aCell.getKey(), bCell.getKey());
                if (combinedAddress == null) continue; // not combinable
                cells.put(combinedAddress, aCell.getValue() * bCell.getValue());
            }
        }
    }

    private TensorAddress combine(TensorAddress a, TensorAddress b) {
        List<TensorAddress.Element> combined = new ArrayList<>();
        combined.addAll(dense(a, dimensionsA));
        combined.addAll(dense(b, dimensionsB));
        Collections.sort(combined);
        TensorAddress.Element previous = null;
        for (ListIterator<TensorAddress.Element> i = combined.listIterator(); i.hasNext(); ) {
            TensorAddress.Element current = i.next();
            if (previous != null && previous.dimension().equals(current.dimension())) { // an overlapping dimension
                if (previous.label().equals(current.label()))
                    i.remove(); // a match: remove the duplicate
                else
                    return null; // no match: a combination isn't viable
            }
            previous = current;
        }
        return TensorAddress.fromSorted(sparse(combined));
    }

    /**
     * Returns a set of tensor elements which contains an entry for each dimension including "undefined" values
     * (which are not present in the sparse elements list).
     */
    private List<TensorAddress.Element> dense(TensorAddress sparse, Set<String> dimensions) {
        if (sparse.elements().size() == dimensions.size()) return sparse.elements();

        List<TensorAddress.Element> dense = new ArrayList<>(sparse.elements());
        for (String dimension : dimensions) {
        }
        return dense;
    }

    /**
     * Removes any "undefined" entries from the given elements.
     */
    private List<TensorAddress.Element> sparse(List<TensorAddress.Element> dense) {
        List<TensorAddress.Element> sparse = new ArrayList<>();
        for (TensorAddress.Element element : dense) {
        }
        return sparse;
    }

    /** Returns the result of taking this product */
    public Tensor result() {
        return new MapTensor(dimensions, cells.build());
    }

}
