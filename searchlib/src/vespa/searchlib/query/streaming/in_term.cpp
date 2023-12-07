// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "in_term.h"
#include <vespa/searchlib/fef/itermdata.h>
#include <vespa/searchlib/fef/matchdata.h>
#include <vespa/searchlib/query/tree/term_vector.h>
#include <vespa/vespalib/stllike/hash_set.h>

using search::fef::ITermData;
using search::fef::MatchData;
using search::query::TermVector;

namespace search::streaming {

InTerm::InTerm(std::unique_ptr<QueryNodeResultBase> result_base, const string & index, std::unique_ptr<TermVector> terms)
    : MultiTerm(std::move(result_base), index, Type::WORD, std::move(terms))
{
}

InTerm::~InTerm() = default;

void
InTerm::unpack_match_data(uint32_t docid, const ITermData& td, MatchData& match_data)
{
    vespalib::hash_set<uint32_t> matching_field_ids;
    HitList hl_store;
    std::optional<uint32_t> prev_field_id;
    for (const auto& term : _terms) {
        auto& hl = term->evaluateHits(hl_store);
        for (auto& hit : hl) {
            if (!prev_field_id.has_value() || prev_field_id.value() != hit.context()) {
                prev_field_id = hit.context();
                matching_field_ids.insert(hit.context());
            }
        }
    }
    auto num_fields = td.numFields();
    for (uint32_t field_idx = 0; field_idx < num_fields; ++field_idx) {
        auto& tfd = td.field(field_idx);
        auto field_id = tfd.getFieldId();
        if (matching_field_ids.contains(field_id)) {
            auto handle = tfd.getHandle();
            if (handle != fef::IllegalHandle) {
                auto tmd = match_data.resolveTermField(tfd.getHandle());
                tmd->setFieldId(field_id);
                tmd->reset(docid);
            }
        }
    }
}

}
