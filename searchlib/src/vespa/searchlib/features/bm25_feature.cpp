// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "bm25_feature.h"
#include <vespa/searchlib/fef/itermdata.h>
#include <vespa/searchlib/fef/itermfielddata.h>
#include <memory>

namespace search::features {

using fef::Blueprint;
using fef::FeatureExecutor;
using fef::FieldInfo;
using fef::ITermData;
using fef::ITermFieldData;
using fef::MatchDataDetails;

Bm25Executor::Bm25Executor(const fef::FieldInfo& field,
                           const fef::IQueryEnvironment& env)
    : FeatureExecutor(),
      _terms(),
      _avg_field_length(10),
      _k1_param(1.2),
      _b_param(0.75)
{
    // TODO: Don't use hard coded avg_field_length
    // TODO: Add support for setting k1 and b
    for (size_t i = 0; i < env.getNumTerms(); ++i) {
        const ITermData* term = env.getTerm(i);
        for (size_t j = 0; j < term->numFields(); ++j) {
            const ITermFieldData& term_field = term->field(j);
            if (field.id() == term_field.getFieldId()) {
                // TODO: Add proper calculation of IDF
                _terms.emplace_back(term_field.getHandle(MatchDataDetails::Cheap), 1.0);
            }
        }
    }
}

void
Bm25Executor::handle_bind_match_data(const fef::MatchData& match_data)
{
    for (auto& term : _terms) {
        term.tfmd = match_data.resolveTermField(term.handle);
    }
}

void
Bm25Executor::execute(uint32_t doc_id)
{
    feature_t score = 0;
    for (const auto& term : _terms) {
        if (term.tfmd->getDocId() == doc_id) {
            feature_t num_occs = term.tfmd->getNumOccs();
            feature_t norm_field_length = ((feature_t)term.tfmd->getFieldLength()) / _avg_field_length;

            feature_t numerator = term.inverse_doc_freq * num_occs * (_k1_param + 1);
            feature_t denominator = num_occs + (_k1_param * (1 - _b_param + (_b_param * norm_field_length)));

            score += numerator / denominator;
        }
    }
    outputs().set_number(0, score);
}


Bm25Blueprint::Bm25Blueprint()
    : Blueprint("bm25"),
      _field(nullptr)
{
}

void
Bm25Blueprint::visitDumpFeatures(const fef::IIndexEnvironment& env, fef::IDumpFeatureVisitor& visitor) const
{
    (void) env;
    (void) visitor;
    // TODO: Implement
}

fef::Blueprint::UP
Bm25Blueprint::createInstance() const
{
    return std::make_unique<Bm25Blueprint>();
}

bool
Bm25Blueprint::setup(const fef::IIndexEnvironment& env, const fef::ParameterList& params)
{
    const auto& field_name = params[0].getValue();
    _field = env.getFieldByName(field_name);

    describeOutput("score", "The bm25 score for all terms searching in the given index field");
    return (_field != nullptr);
}

fef::FeatureExecutor&
Bm25Blueprint::createExecutor(const fef::IQueryEnvironment& env, vespalib::Stash& stash) const
{
    return stash.create<Bm25Executor>(*_field, env);
}

}
