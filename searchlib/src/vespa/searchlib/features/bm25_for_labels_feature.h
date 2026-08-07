// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "bm25_utils.h"

#include <vespa/eval/eval/value.h>
#include <vespa/eval/eval/value_type.h>
#include <vespa/searchlib/common/feature.h>
#include <vespa/searchlib/fef/blueprint.h>
#include <vespa/searchlib/fef/featureexecutor.h>
#include <vespa/vespalib/util/shared_string_repo.h>

#include <memory>
#include <optional>
#include <string>
#include <utility>
#include <vector>

namespace search::features {

/**
 * Executor calculating the BM25 score in a single index field for the terms carrying each query item
 * label, as a tensor(label{}) with one cell per label that scored for the document.
 */
class Bm25ForLabelsExecutor : public fef::FeatureExecutor {
    using QueryTerm = Bm25Utils::QueryTerm;

    std::vector<std::vector<QueryTerm>> _terms_per_label; // in label order
    double                              _avg_field_length;

    // The 'k1' param determines term frequency saturation characteristics.
    // The 'b' param adjusts the effects of the field length of the document matched compared to the average field
    // length.
    double _k1_mul_b;
    double _k1_mul_one_minus_b;

    vespalib::SharedStringRepo::Handles    _labels;       // every candidate label, resolved once
    vespalib::StringIdVector               _view_labels;  // per document scoring subset, non-owning
    std::vector<double>                    _view_cells;   // per document scores, parallel to _view_labels
    const vespalib::eval::Value&           _empty_output; // owned by the blueprint
    std::unique_ptr<vespalib::eval::Value> _output;

    feature_t term_score(const QueryTerm& term, uint32_t doc_id) const;

public:
    Bm25ForLabelsExecutor(std::vector<std::pair<std::string, std::vector<QueryTerm>>> labeled_terms,
                          double avg_field_length, double k1_param, double b_param,
                          const vespalib::eval::Value& empty_output);
    ~Bm25ForLabelsExecutor() override;

    void handle_bind_match_data(const fef::MatchData& match_data) override;
    void execute(uint32_t docId) override;
};

/**
 * Blueprint for the BM25 score in a given index field of the terms carrying each query item label,
 * exposed as a mapped tensor(label{}) with the query item labels as cell labels. A label gets a cell
 * only when it actually scores, i.e. when it is carried by at least one query term searching the given
 * field and at least one of those terms matched the document. Tuning properties are the ones of
 * bm25(<field>), so that bm25_for_labels(<field>){label:x} equals bm25(field: <field>, label: x).
 */
class Bm25ForLabelsBlueprint : public fef::Blueprint {
private:
    const fef::FieldInfo*                  _field;
    double                                 _k1_param;
    double                                 _b_param;
    std::optional<double>                  _avg_field_length;
    vespalib::eval::ValueType              _value_type;
    std::unique_ptr<vespalib::eval::Value> _empty_output;

public:
    Bm25ForLabelsBlueprint();
    ~Bm25ForLabelsBlueprint() override;

    void visitDumpFeatures(const fef::IIndexEnvironment& env, fef::IDumpFeatureVisitor& visitor) const override;
    fef::Blueprint::UP createInstance() const override;
    fef::ParameterDescriptions getDescriptions() const override;
    bool setup(const fef::IIndexEnvironment& env, const fef::ParameterList& params) override;
    void prepareSharedState(const fef::IQueryEnvironment& env, fef::IObjectStore& store) const override;
    fef::FeatureExecutor& createExecutor(const fef::IQueryEnvironment& env, vespalib::Stash& stash) const override;
};

} // namespace search::features
