// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "fieldsearcher.h"
#include <vespa/eval/eval/value_type.h>
#include <vespa/searchcommon/attribute/distance_metric.h>
#include <vespa/searchlib/tensor/distance_calculator.h>
#include <vespa/searchlib/tensor/tensor_ext_attribute.h>

namespace search::fef { class IQueryEnvironment; }

namespace search::tensor {
class TensorExtAttribute;
}

namespace search::streaming { class NearestNeighborQueryNode; }

namespace vsm {

/**
 * Class used to perform exact nearest neighbor search over the streamed values of a tensor field.
 *
 * The raw score from the distance calculation is stored in NearestNeighborQueryNode instances
 * searching this field.
 */
class NearestNeighborFieldSearcher : public FieldSearcher {
private:
    struct NodeAndCalc {
        search::streaming::NearestNeighborQueryNode* node;
        std::unique_ptr<search::tensor::DistanceCalculator> calc;
        double distance_threshold;
        NodeAndCalc(search::streaming::NearestNeighborQueryNode* node_in,
                    std::unique_ptr<search::tensor::DistanceCalculator> calc_in);
    };
    search::attribute::DistanceMetric _metric;
    std::unique_ptr<search::tensor::TensorExtAttribute> _attr;
    std::vector<NodeAndCalc> _calcs;

public:
    NearestNeighborFieldSearcher(const FieldIdT& fid,
                                 search::attribute::DistanceMetric metric);

    std::unique_ptr<FieldSearcher> duplicate() const override;
    // TODO: change FieldSearcher::prepare() to provide the necessary objects.
    void prepare_new(search::streaming::QueryTermList& qtl, const SharedSearcherBuf& buf,
                     const vespalib::eval::ValueType& tensor_type, search::fef::IQueryEnvironment& query_env);
    void onValue(const document::FieldValue& fv) override;

    static search::attribute::DistanceMetric distance_metric_from_string(const vespalib::string& value);
};

}
