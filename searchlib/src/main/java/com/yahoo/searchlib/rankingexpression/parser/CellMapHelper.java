// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.rankingexpression.parser;

import com.yahoo.api.annotations.Beta;
import com.yahoo.searchlib.rankingexpression.rule.ExpressionNode;
import com.yahoo.searchlib.rankingexpression.rule.TensorFunctionNode;
import com.yahoo.searchlib.rankingexpression.Reference;
import com.yahoo.tensor.IndexedTensor;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorAddress;
import com.yahoo.tensor.TensorType;
import com.yahoo.tensor.functions.ScalarFunction;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Helper to incrementally build addresses and insert values (expression nodes) into a map.
 * Only used as an internal component of the RankingExpression parser.
 * @author arnej
 */
public class CellMapHelper {

    private record Common(Map<TensorAddress, ScalarFunction<Reference>> receivingMap,
                          TensorType type,
                          List<String> mappedDims,
                          List<String> indexedDims)
    {
        Common(TensorType type, List<String> dimensionOrder) {
            this(new LinkedHashMap<>(), type, new ArrayList<>(), new ArrayList<>());
            for (String name : dimensionOrder) {
                var dim = type.dimension(name)
                        .orElseThrow(() -> new IllegalStateException("bad dimension name: " + name));
                if (dim.isMapped()) {
                    mappedDims.add(name);
                } else {
                    indexedDims.add(name);
                }
            }
        }
    }

    private final Common meta;
    private final List<String> labels;

    public CellMapHelper(TensorType type, List<String> dimensionOrder) {
        this.meta = new Common(type, dimensionOrder);
        this.labels = new ArrayList<>();
    }

    private CellMapHelper(Common meta, List<String> labels) {
        this.meta = meta;
        this.labels = labels;
    }

    public Map<TensorAddress, ScalarFunction<Reference>> map() {
        return meta.receivingMap();
    }

    public CellMapHelper bind(String label) {
        if (labels.size() >= meta.mappedDims().size()) {
            throw new IllegalArgumentException("At " + address() + ": Got label '" + label +
                                               "' but all mapped dimensions already have labels");
        }
        List<String> next = new ArrayList<>(labels);
        next.add(label);
        return new CellMapHelper(meta, next);
    }

    private String address() {
        var addr = new StringBuilder();
        if (labels.size() == 1) {
            addr.append("'");
            addr.append(labels.get(0));
            addr.append("'");
        } else {
            addr.append("{");
            for (int idx = 0; idx < labels.size(); idx++) {
                if (idx > 0) {
                    addr.append(", ");
                }
                addr.append(meta.mappedDims().get(idx));
                addr.append(":'");
                addr.append(labels.get(idx));
                addr.append("'");
            }
            addr.append("}");
        }
        return addr.toString();
    }

    public void handleScalar(ExpressionNode node) {
        if (labels.size() < meta.mappedDims().size()) {
            throw new IllegalArgumentException("At " + address() + ": Missing label for dimension '" +
                                               meta.mappedDims().get(labels.size()) + "'");
        }
        if (meta.type().hasIndexedDimensions()) {
            throw new IllegalArgumentException("At " + address() + ": Need an array of values");
        }
        var addr = new TensorAddress.Builder(meta.type());
        for (int idx = 0; idx < labels.size(); idx++) {
            addr.add(meta.mappedDims().get(idx), labels.get(idx));
        }
        meta.receivingMap().put(addr.build(), TensorFunctionNode.wrapScalar(node));
    }

    public void handleDenseSubspace(List<ExpressionNode> nodes) {
        if (labels.size() < meta.mappedDims().size()) {
            throw new IllegalArgumentException("At " + address() + ": Missing label for dimension '" +
                                               meta.mappedDims().get(labels.size()) + "'");
        }
        if (! meta.type().hasIndexedDimensions()) {
            throw new IllegalArgumentException("At " + address() + ": Need a single value");
        }
        TensorType denseSubtype = meta.type().indexedSubtype();
        IndexedTensor.Indexes indexes = IndexedTensor.Indexes.of(denseSubtype, meta.indexedDims());
        if (indexes.size() != nodes.size()) {
            throw new IllegalArgumentException("At " + address() +
                                               ": Need " + indexes.size() +
                                               " values to fill a dense subspace of " + meta.type() +
                                               " but got " + nodes.size());
        }
        var addr = new TensorAddress.Builder(meta.type());
        for (int idx = 0; idx < labels.size(); idx++) {
            addr.add(meta.mappedDims().get(idx), labels.get(idx));
        }
        for (ExpressionNode node : nodes) {
            indexes.next();
            int indexedDimensionsIndex = 0;
            for (TensorType.Dimension dimension : meta.type().dimensions()) {
                if (dimension.isIndexed()) {
                    addr.add(dimension.name(), indexes.indexesForReading()[indexedDimensionsIndex++]);
                }
            }
            meta.receivingMap().put(addr.build(), TensorFunctionNode.wrapScalar(node));
        }
    }

}
