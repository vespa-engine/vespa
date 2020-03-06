// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.search.searchers;

import com.google.common.annotations.Beta;

import com.yahoo.prelude.query.Item;
import com.yahoo.prelude.query.NearestNeighborItem;
import com.yahoo.prelude.query.QueryCanonicalizer;
import com.yahoo.prelude.query.ToolBox;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.query.ranking.RankProperties;
import com.yahoo.search.result.ErrorMessage;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;
import com.yahoo.vespa.config.search.AttributesConfig;
import com.yahoo.yolean.chain.After;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Validates any NearestNeighborItem query items.
 *
 * @author arnej
 */
@Beta
// This depends on tensors in query.getRanking which are moved to rank.properties during query.prepare()
// Query.prepare is done at the same time as canonicalization (by GroupingExecutor), so use that constraint.
@After(QueryCanonicalizer.queryCanonicalization)
public class ValidateNearestNeighborSearcher extends Searcher {

    private Map<String, TensorType> validAttributes = new HashMap<>();

    public ValidateNearestNeighborSearcher(AttributesConfig attributesConfig) {
        for (AttributesConfig.Attribute a : attributesConfig.attribute()) {
            TensorType tt = null;
            if (a.datatype() == AttributesConfig.Attribute.Datatype.TENSOR) {
                tt = TensorType.fromSpec(a.tensortype());
            }
            validAttributes.put(a.name(), tt);
        }
    }

    @Override
    public Result search(Query query, Execution execution) {
        Optional<ErrorMessage> e = validate(query);
        return e.isEmpty() ? execution.search(query) : new Result(query, e.get());
    }

    private Optional<ErrorMessage> validate(Query query) {
        NNVisitor visitor = new NNVisitor(query.getRanking().getProperties(), validAttributes);
        ToolBox.visit(visitor, query.getModel().getQueryTree().getRoot());
        return visitor.errorMessage;
    }

    private static class NNVisitor extends ToolBox.QueryVisitor {

        public Optional<ErrorMessage> errorMessage = Optional.empty();

        private RankProperties rankProperties;
        private Map<String, TensorType> validAttributes;

        public NNVisitor(RankProperties rankProperties, Map<String, TensorType> validAttributes) {
            this.rankProperties = rankProperties;
            this.validAttributes = validAttributes;
        }

        @Override
        public boolean visit(Item item) {
            if (item instanceof NearestNeighborItem) {
                String error = validate((NearestNeighborItem) item);
                if (error != null)
                    errorMessage = Optional.of(ErrorMessage.createIllegalQuery(error));
            }
            return true;
        }

        private static boolean isCompatible(TensorType lhs, TensorType rhs) {
            return lhs.dimensions().equals(rhs.dimensions());
        }

        private static boolean isDenseVector(TensorType tt) {
            List<TensorType.Dimension> dims = tt.dimensions();
            if (dims.size() != 1) return false;
            for (var d : dims) {
                if (d.type() != TensorType.Dimension.Type.indexedBound) return false;
            }
            return true;
        }

        /** Returns an error message if this is invalid, or null if it is valid */
        private String validate(NearestNeighborItem item) {
            int target = item.getTargetNumHits();
            if (target < 1) return item + " has invalid targetNumHits";

            List<Object> rankPropValList = rankProperties.asMap().get(item.getQueryTensorName());
            if (rankPropValList == null) return item + " query tensor not found";
            if (rankPropValList.size() != 1) return item + " query tensor does not have a single value";

            Object rankPropValue = rankPropValList.get(0);
            if (! (rankPropValue instanceof Tensor)) {
                return item + " query tensor should be a tensor, was: " +
                       (rankPropValue == null ? "null" : rankPropValue.getClass());
            }

            String field = item.getIndexName();
            if ( ! validAttributes.containsKey(field)) return item + " field is not an attribute";

            TensorType fTensorType = validAttributes.get(field);
            TensorType qTensorType = ((Tensor)rankPropValue).type();
            if (fTensorType == null) return item + " field is not a tensor";
            if ( ! isCompatible(fTensorType, qTensorType)) {
                return item + " field type " + fTensorType + " does not match query tensor type " + qTensorType;
            }
            if (! isDenseVector(fTensorType)) return item + " tensor type " + fTensorType+" is not a dense vector";
            return null;
        }

        @Override
        public void onExit() {}

    }

}
