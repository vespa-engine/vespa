// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.tensor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import static com.yahoo.tensor.TensorType.Dimension;
import static com.yahoo.tensor.TensorType.Value;

/**
 * Common type resolving for basic tensor operations.
 *
 * @author arnej
 */
public class TypeResolver {

    private static final Logger logger = Logger.getLogger(TypeResolver.class.getName());

    static private TensorType scalar() {
        return TensorType.empty;
    }

    static public TensorType map(TensorType inputType) {
        Value orig = inputType.valueType();
        Value cellType = Value.largestOf(orig, Value.FLOAT);
        if (cellType == orig) {
            return inputType;
        }
        return new TensorType(cellType, inputType.dimensions());
    }

    static public TensorType reduce(TensorType inputType, List<String> reduceDimensions) {
        if (reduceDimensions.isEmpty()) {
            return scalar();
        }
        Map<String, Dimension> map = new HashMap<>();
        for (Dimension dim : inputType.dimensions()) {
            map.put(dim.name(), dim);
        }
        for (String name : reduceDimensions) {
            if (map.containsKey(name)) {
                map.remove(name);
            } else {
                logger.log(Level.WARNING, "reducing non-existing dimension "+name+" in type "+inputType);
                // throw new IllegalArgumentException("reducing non-existing dimension "+name+" in type "+inputType);
            }
        }
        if (map.isEmpty()) {
            return scalar();
        }
        Value cellType = Value.largestOf(inputType.valueType(), Value.FLOAT);
        return new TensorType(cellType, map.values());
    }

    static public TensorType peek(TensorType inputType, List<String> peekDimensions) {
        if (peekDimensions.isEmpty()) {
            throw new IllegalArgumentException("Peeking no dimensions makes no sense");
        }
        Map<String, Dimension> map = new HashMap<>();
        for (Dimension dim : inputType.dimensions()) {
            map.put(dim.name(), dim);
        }
        for (String name : peekDimensions) {
            if (map.containsKey(name)) {
                map.remove(name);
            } else {
                throw new IllegalArgumentException("Peeking non-existing dimension '" + name + "'");
            }
        }
        if (map.isEmpty()) {
            return scalar();
        }
        Value cellType = inputType.valueType();
        return new TensorType(cellType, map.values());
    }

    static public TensorType rename(TensorType inputType, List<String> from, List<String> to) {
        if (from.isEmpty()) {
            throw new IllegalArgumentException("Renaming no dimensions");
        }
        if (from.size() != to.size()) {
            throw new IllegalArgumentException("Bad rename, from size "+from.size()+" != to.size "+to.size());
        }
        Map<String,Dimension> oldDims = new HashMap<>();
        for (Dimension dim : inputType.dimensions()) {
            oldDims.put(dim.name(), dim);
        }
        Map<String,Dimension> newDims = new HashMap<>();
        for (int i = 0; i < from.size(); ++i) {
            String oldName = from.get(i);
            String newName = to.get(i);
            if (oldDims.containsKey(oldName)) {
                var dim = oldDims.remove(oldName);
                newDims.put(newName, dim.withName(newName));
            } else {
                logger.log(Level.WARNING, "Renaming non-existing dimension "+oldName+" in type "+inputType);
                // throw new IllegalArgumentException("bad rename, dimension  "+oldName+" not found");
            }
        }
        for (var keep : oldDims.values()) {
            newDims.put(keep.name(), keep);
        }
        if (inputType.dimensions().size() == newDims.size()) {
            return new TensorType(inputType.valueType(), newDims.values());
        } else {
            throw new IllegalArgumentException("Bad rename, lost some dimensions");
        }
    }

    static public TensorType cell_cast(TensorType inputType, Value toCellType) {
        if (toCellType != Value.DOUBLE && inputType.dimensions().isEmpty()) {
            throw new IllegalArgumentException("Cannot cast "+inputType+" to valueType"+toCellType);
        }
        return new TensorType(toCellType, inputType.dimensions());
    }

    private static boolean firstIsBoundSecond(Dimension first, Dimension second) {
        return (first.type() == Dimension.Type.indexedBound &&
                second.type() == Dimension.Type.indexedUnbound &&
                first.name().equals(second.name()));
    }

    private static boolean firstIsSmaller(Dimension first, Dimension second) {
        return (first.type() == Dimension.Type.indexedBound &&
                second.type() == Dimension.Type.indexedBound &&
                first.name().equals(second.name()) &&
                first.size().isPresent() && second.size().isPresent() &&
                first.size().get() < second.size().get());
    }

    static public TensorType join(TensorType lhs, TensorType rhs) {
        Value cellType = Value.DOUBLE;
        if (lhs.rank() > 0 && rhs.rank() > 0) {
            // both types decide the new cell type
            cellType = Value.largestOf(lhs.valueType(), rhs.valueType());
        } else if (lhs.rank() > 0) {
            // only the tensor decide the new cell type
            cellType = lhs.valueType();
        } else if (rhs.rank() > 0) {
            // only the tensor decide the new cell type
            cellType = rhs.valueType();
        }
        // result of computation must be at least float
        cellType = Value.largestOf(cellType, Value.FLOAT);

        Map<String, Dimension> map = new HashMap<>();
        for (Dimension dim : lhs.dimensions()) {
            map.put(dim.name(), dim);
        }
        for (Dimension dim : rhs.dimensions()) {
            if (map.containsKey(dim.name())) {
                Dimension other = map.get(dim.name());
                if (! other.equals(dim)) {
                    if (firstIsBoundSecond(dim, other)) {
                        map.put(dim.name(), dim);
                    } else if (firstIsBoundSecond(other, dim)) {
                        map.put(dim.name(), other);
                    } else if (dim.isMapped() && other.isIndexed()) {
                        map.put(dim.name(), dim);  // {} and [] -> {}. Note: this is not allowed in C++
                    } else if (dim.isIndexed() && other.isMapped()) {
                        map.put(dim.name(), other);  // {} and [] -> {}. Note: this is not allowed in C++
                    } else {
                        throw new IllegalArgumentException("Unequal dimension " + dim.name() + " in " + lhs+ " and "+rhs);
                    }
                }
            } else {
                map.put(dim.name(), dim);
            }
        }
        return new TensorType(cellType, map.values());
    }

    static public TensorType merge(TensorType lhs, TensorType rhs) {
        int sz = lhs.dimensions().size();
        boolean allOk = (rhs.dimensions().size() == sz);
        if (allOk) {
            for (int i = 0; i < sz; i++) {
                String lName = lhs.dimensions().get(i).name();
                String rName = rhs.dimensions().get(i).name();
                if (! lName.equals(rName)) {
                    allOk = false;
                }
            }
        }
        if (allOk) {
            return join(lhs, rhs);
        } else {
            throw new IllegalArgumentException("Types in merge() dimensions mismatch: "+lhs+" != "+rhs);
        }
    }

    static public TensorType concat(TensorType lhs, TensorType rhs, String concatDimension) {
        Value cellType = Value.DOUBLE;
        if (lhs.rank() > 0 && rhs.rank() > 0) {
            if (lhs.valueType() == rhs.valueType()) {
                cellType = lhs.valueType();
            } else {
                cellType = Value.largestOf(lhs.valueType(), rhs.valueType());
                // when changing cell type, make it at least float
                cellType = Value.largestOf(cellType, Value.FLOAT);
            }
        } else if (lhs.rank() > 0) {
            cellType = lhs.valueType();
        } else if (rhs.rank() > 0) {
            cellType = rhs.valueType();
        }
        Dimension first = Dimension.indexed(concatDimension, 1);
        Dimension second = Dimension.indexed(concatDimension, 1);
        Map<String, Dimension> map = new HashMap<>();
        for (Dimension dim : lhs.dimensions()) {
            if (dim.name().equals(concatDimension)) {
                first = dim;
            } else {
                map.put(dim.name(), dim);
            }
        }
        for (Dimension dim : rhs.dimensions()) {
            if (dim.name().equals(concatDimension)) {
                second = dim;
            } else if (map.containsKey(dim.name())) {
                Dimension other = map.get(dim.name());
                if (! other.equals(dim)) {
                    if (firstIsBoundSecond(dim, other)) {
                        map.put(dim.name(), other);  // [N] and [] -> []
                    } else if (firstIsBoundSecond(other, dim)) {
                        map.put(dim.name(), dim);  // [N] and [] -> []
                    } else if (firstIsSmaller(dim, other)) {
                        map.put(dim.name(), dim); // [N] and [M] -> [ min(N,M] ].
                    } else if (firstIsSmaller(other, dim)) {
                        map.put(dim.name(), other); // [N] and [M] -> [ min(N,M] ].
                    } else {
                        throw new IllegalArgumentException("Unequal dimension " + dim.name() + " in " + lhs+ " and "+rhs);
                    }
                }
            } else {
                map.put(dim.name(), dim);
            }
        }
        if (first.type() == Dimension.Type.mapped) {
            throw new IllegalArgumentException("Bad concat dimension "+concatDimension+" in lhs: "+lhs);
        }
        if (second.type() == Dimension.Type.mapped) {
            throw new IllegalArgumentException("Bad concat dimension "+concatDimension+" in rhs: "+rhs);
        }
        if (first.type() == Dimension.Type.indexedUnbound) {
            map.put(concatDimension, first);
        } else if (second.type() == Dimension.Type.indexedUnbound) {
            map.put(concatDimension, second);
        } else {
            long concatSize = first.size().get() + second.size().get();
            map.put(concatDimension, Dimension.indexed(concatDimension, concatSize));
        }
        return new TensorType(cellType, map.values());
    }

}

