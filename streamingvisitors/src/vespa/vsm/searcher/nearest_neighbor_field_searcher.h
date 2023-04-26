// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "fieldsearcher.h"
#include <vespa/eval/eval/value_type.h>
#include <vespa/searchcommon/attribute/distance_metric.h>
#include <vespa/searchlib/query/streaming/nearest_neighbor_query_node.h>
#include <vespa/searchlib/queryeval/nearest_neighbor_distance_heap.h>
#include <vespa/searchlib/tensor/distance_calculator.h>
#include <vespa/searchlib/tensor/tensor_ext_attribute.h>

namespace search::fef { class IQueryEnvironment; }

namespace search::tensor {
class TensorExtAttribute;
}

namespace vsm {

/**
 * Class used to perform exact nearest neighbor search over the streamed values of a tensor field.
 *
 * The raw score from the distance calculation is stored in NearestNeighborQueryNode instances
 * searching this field.
 */
class NearestNeighborFieldSearcher : public FieldSearcher {
private:
    class NodeAndCalc : search::streaming::NearestNeighborQueryNode::RawScoreCalculator {
    public:
        search::streaming::NearestNeighborQueryNode* node;
        std::unique_ptr<search::tensor::DistanceCalculator> calc;
        search::queryeval::NearestNeighborDistanceHeap heap;
        NodeAndCalc(search::streaming::NearestNeighborQueryNode* node_in,
                    std::unique_ptr<search::tensor::DistanceCalculator> calc_in);

        double to_raw_score(double distance) override;
    };
    search::attribute::DistanceMetric _metric;
    std::unique_ptr<search::tensor::TensorExtAttribute> _attr;
    std::vector<std::unique_ptr<NodeAndCalc>> _calcs;

public:
    NearestNeighborFieldSearcher(FieldIdT fid,
                                 search::attribute::DistanceMetric metric);
    ~NearestNeighborFieldSearcher();

    std::unique_ptr<FieldSearcher> duplicate() const override;
    void prepare(search::streaming::QueryTermList& qtl,
                 const SharedSearcherBuf& buf,
                 const vsm::FieldPathMapT& field_paths,
                 search::fef::IQueryEnvironment& query_env) override;
    void onValue(const document::FieldValue& fv) override;

    static search::attribute::DistanceMetric distance_metric_from_string(const vespalib::string& value);
};

}
