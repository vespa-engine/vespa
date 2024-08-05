// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "weighted_set_term.h"
#include <vespa/searchlib/fef/itermdata.h>
#include <vespa/searchlib/fef/matchdata.h>
#include <vespa/vespalib/stllike/hash_map.hpp>

using search::fef::ITermData;
using search::fef::MatchData;

namespace search::streaming {

WeightedSetTerm::WeightedSetTerm(std::unique_ptr<QueryNodeResultBase> result_base, string index, uint32_t num_terms)
    : MultiTerm(std::move(result_base), std::move(index), num_terms)
{
}

WeightedSetTerm::~WeightedSetTerm() = default;

void
WeightedSetTerm::unpack_match_data(uint32_t docid, const ITermData& td, MatchData& match_data, const fef::IIndexEnvironment&)
{
    vespalib::hash_map<uint32_t,std::vector<double>> scores;
    HitList hl_store;
    for (const auto& term : _terms) {
        auto& hl = term->evaluateHits(hl_store);
        for (auto& hit : hl) {
            scores[hit.field_id()].emplace_back(term->weight().percent());
        }
    }
    auto num_fields = td.numFields();
    for (uint32_t field_idx = 0; field_idx < num_fields; ++field_idx) {
        auto& tfd = td.field(field_idx);
        auto field_id = tfd.getFieldId();
        if (scores.contains(field_id)) {
            auto handle = tfd.getHandle();
            if (handle != fef::IllegalHandle) {
                auto &field_scores = scores[field_id];
                std::sort(field_scores.begin(), field_scores.end(), std::greater());
                auto tmd = match_data.resolveTermField(tfd.getHandle());
                tmd->setFieldId(field_id);
                tmd->reset(docid);
                for (auto& field_score : field_scores) {
                    fef::TermFieldMatchDataPosition pos;
                    pos.setElementWeight(field_score);
                    tmd->appendPosition(pos);
                }
            }
        }
    }
}

}
