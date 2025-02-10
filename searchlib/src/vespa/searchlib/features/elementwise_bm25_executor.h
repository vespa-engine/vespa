// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/fef/featureexecutor.h>
#include "bm25_utils.h"
#include "elementwise_output.h"
#include <vespa/vespalib/stllike/hash_map.h>

namespace search::fef { class IQueryEnvironment; }

namespace search::features {

/**
 * Executor for the elementwise bm25 ranking algorithm over a single index field. It calculates aggregated bm25 scores
 * for each element in the field across the terms searching the field. These scores are then used to build an output
 * tensor with a single mapped dimension, with element id as label value and aggregated bm25 score as tensor cell value.
 */
class ElementwiseBm25Executor : public fef::FeatureExecutor {
    using QueryTerm = Bm25Utils::QueryTerm;
    using QueryTermVector = std::vector<QueryTerm>;

    QueryTermVector _terms;
    double _avg_element_length;

    // The 'k1' param determines term frequency saturation characteristics.
    // The 'b' param adjusts the effects of the element length of the document matched compared to the average element length.
    double _k1_mul_b;
    double _k1_mul_one_minus_b;
    vespalib::hash_map<uint32_t, double> _scores; // element id -> aggregated score mapping
    ElementwiseOutput _output;

    void apply_bm25_score(uint32_t num_occs, uint32_t element_id, uint32_t element_length, const QueryTerm& term);
public:
    ElementwiseBm25Executor(const fef::FieldInfo &field,
                            const fef::IQueryEnvironment &env,
                            double avg_element_length,
                            double k1_param,
                            double b_param,
                            const vespalib::eval::Value& empty_output);
    ~ElementwiseBm25Executor() override;
    void handle_bind_match_data(const fef::MatchData& match_data) override;
    void execute(uint32_t docId) override;
};

}
