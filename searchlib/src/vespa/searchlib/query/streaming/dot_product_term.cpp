// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "dot_product_term.h"
#include <vespa/searchlib/fef/itermdata.h>
#include <vespa/searchlib/fef/matchdata.h>
#include <vespa/vespalib/stllike/hash_map.h>

using search::fef::ITermData;
using search::fef::MatchData;

namespace search::streaming {

DotProductTerm::DotProductTerm(std::unique_ptr<QueryNodeResultBase> result_base, const string & index, uint32_t num_terms)
    : MultiTerm(std::move(result_base), index, num_terms)
{
}

DotProductTerm::~DotProductTerm() = default;

void
DotProductTerm::unpack_match_data(uint32_t docid, const ITermData& td, MatchData& match_data)
{
    vespalib::hash_map<uint32_t,double> scores;
    HitList hl_store;
    for (const auto& term : _terms) {
        auto& hl = term->evaluateHits(hl_store);
        for (auto& hit : hl) {
            scores[hit.context()] += term->weight().percent() * hit.weight();
        }
    }
    auto num_fields = td.numFields();
    for (uint32_t field_idx = 0; field_idx < num_fields; ++field_idx) {
        auto& tfd = td.field(field_idx);
        auto field_id = tfd.getFieldId();
        if (scores.contains(field_id)) {
            auto handle = tfd.getHandle();
            if (handle != fef::IllegalHandle) {
                auto tmd = match_data.resolveTermField(tfd.getHandle());
                tmd->setFieldId(field_id);
                tmd->setRawScore(docid, scores[field_id]);
            }
        }
    }
}

}
