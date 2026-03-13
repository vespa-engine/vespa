// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "elementwise_bm25_executor.h"
#include "bm25_utils.h"
#include <vespa/searchlib/fef/itermdata.h>
#include <vespa/searchlib/fef/iqueryenvironment.h>
#include <vespa/searchlib/fef/match_data_details.h>

namespace search::features {

using fef::ITermData;
using fef::ITermFieldData;
using fef::MatchDataDetails;
using vespalib::eval::Value;


ElementwiseBm25Executor::ElementwiseBm25Executor(const fef::FieldInfo& field,
                           const fef::IQueryEnvironment& env,
                           double avg_element_length,
                           double k1_param,
                           double b_param,
                           const Value& empty_output)
    : FeatureExecutor(),
      _terms(),
      _avg_element_length(avg_element_length),
      _k1_mul_b(k1_param * b_param),
      _k1_mul_one_minus_b(k1_param * (1 - b_param)),
      _scores(),
      _output(empty_output)
{
    for (size_t i = 0; i < env.getNumTerms(); ++i) {
        const ITermData* term = env.getTerm(i);
        for (size_t j = 0; j < term->numFields(); ++j) {
            const ITermFieldData& term_field = term->field(j);
            if (field.id() == term_field.getFieldId()) {
                _terms.emplace_back(term_field.getHandle(MatchDataDetails::Normal),
                                    Bm25Utils::get_inverse_document_frequency(term_field, env, *term),
                                    k1_param);
            }
        }
    }
}

ElementwiseBm25Executor::~ElementwiseBm25Executor() = default;

void
ElementwiseBm25Executor::handle_bind_match_data(const fef::MatchData& match_data)
{
    for (auto& term : _terms) {
        term.tfmd = match_data.resolveTermField(term.handle);
    }
}

void
ElementwiseBm25Executor::apply_bm25_score(uint32_t num_occs, uint32_t element_id, uint32_t element_length,
                                          const QueryTerm& term)
{
    feature_t norm_element_length = ((feature_t) element_length) / _avg_element_length;
    feature_t numerator = num_occs * term.idf_mul_k1_plus_one;
    feature_t denominator = num_occs + (_k1_mul_one_minus_b + _k1_mul_b * norm_element_length);
    feature_t score = numerator / denominator;
    auto ins_res = _scores.insert(std::make_pair(element_id, score));
    if (!ins_res.second) {
        ins_res.first->second += score;
    }
}

void
ElementwiseBm25Executor::execute(uint32_t doc_id)
{
    _scores.clear();
    uint32_t element_id = 0;
    uint32_t element_length = 0;
    uint32_t num_occs = 0;
    for (const auto& term : _terms) {
        if (term.tfmd->has_ranking_data(doc_id)) {
            num_occs = 0;
            for (auto& pos : *term.tfmd) {
                if (num_occs > 0 && element_id == pos.getElementId()) {
                    ++num_occs;
                } else {
                    if (num_occs > 0) {
                        apply_bm25_score(num_occs, element_id, element_length, term);
                    }
                    num_occs = 1;
                    element_id = pos.getElementId();
                    element_length = pos.getElementLen();
                }
            }
            if (num_occs > 0) {
                apply_bm25_score(num_occs, element_id, element_length, term);
            }
        }
    }
    outputs().set_object(0, _output.build(_scores));
}

}
