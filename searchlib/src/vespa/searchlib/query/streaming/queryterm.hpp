// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "queryterm.h"
#include <vespa/searchlib/fef/iindexenvironment.h>
#include <vespa/searchlib/fef/itermdata.h>
#include <vespa/searchlib/fef/matchdata.h>
#include <algorithm>
#include <limits>
#include <optional>


namespace search::streaming {

namespace {

uint16_t
cap_16_bits(uint32_t value)
{
    return std::min(value, static_cast<uint32_t>(std::numeric_limits<uint16_t>::max()));
}

uint32_t
extract_field_length(const QueryTerm& term, uint32_t field_id)
{
    return (field_id < term.getFieldInfoSize()) ? term.getFieldInfo(field_id).getFieldLength() : search::fef::FieldPositionsIterator::UNKNOWN_LENGTH;
}

void
set_interleaved_features(search::fef::TermFieldMatchData& tmd, uint32_t field_length, uint32_t num_occs)
{
    tmd.setFieldLength(cap_16_bits(field_length));
    tmd.setNumOccs(cap_16_bits(num_occs));
}

}

template <typename HitListType>
void
QueryTerm::unpack_match_data_helper(uint32_t docid, const fef::ITermData& td, fef::MatchData& match_data,
                                    const HitListType& hit_list, const QueryTerm& fl_term, bool term_filter,
                                    const fef::IIndexEnvironment& index_env, search::common::ElementIds element_ids)
{
    (void) element_ids;
    (void) fl_term;
    if (!hit_list.empty()) { // only unpack if we have a hit

        uint32_t last_field_id = -1;
        uint32_t last_field_length = 0;
        search::fef::TermFieldMatchData *tmd = nullptr;
        uint32_t num_occs = 0;
        bool filter = false;
        std::optional<search::common::ElementIds::iterator> element_ids_it;

        // optimize for hitlist giving all hits for a single field in one chunk
        for (const auto& hit : hit_list) {
            uint32_t field_id = hit.field_id();
            if (field_id != last_field_id) {
                if (tmd != nullptr) {
                    if (num_occs != 0u) {
                        if (tmd->needs_interleaved_features() && !filter) {
                            set_interleaved_features(*tmd, last_field_length, num_occs);
                        }
                    } else {
                        tmd->resetOnlyDocId(search::fef::TermFieldMatchData::invalidId());
                    }
                    // reset to notfound/unknown values
                    tmd = nullptr;
                }
                num_occs = 0;
                auto field = index_env.getField(field_id);
                filter = term_filter || (field != nullptr && field->isFilter());
                if (!element_ids.all_elements()) {
                    element_ids_it.emplace(element_ids.begin());
                }

                // setup for new field that had a hit
                const search::fef::ITermFieldData *tfd = td.lookupField(field_id);
                if (tfd != nullptr) {
                    tmd = match_data.resolveTermField(tfd->getHandle());
                    tmd->setFieldId(field_id);
                    // reset field match data, but only once per docId
                    if (!tmd->has_data(docid)) {
                        tmd->reset(docid);
                    }
                }
                last_field_id = field_id;
                if constexpr (std::is_same_v<HitList, HitListType>) {
                    last_field_length = extract_field_length(fl_term, field_id);
                } else {
                    last_field_length = hit.get_field_length();
                }
            }
            if (element_ids_it.has_value()) {
                auto& it = element_ids_it.value();
                while (it != element_ids.end() && *it < hit.element_id()) {
                    ++it;
                }
                if (it == element_ids.end() || *it != hit.element_id()) {
                    continue; // Element is filtered
                }
            }
            ++num_occs;
            if (tmd != nullptr && !filter) {
                search::fef::TermFieldMatchDataPosition pos(hit.element_id(), hit.position(),
                                               hit.element_weight(), hit.element_length());
                tmd->appendPosition(pos);
            }
        }
        if (tmd != nullptr) {
            if (num_occs != 0u) {
                if (tmd->needs_interleaved_features() && !filter) {
                    set_interleaved_features(*tmd, last_field_length, num_occs);
                }
            } else {
                tmd->resetOnlyDocId(search::fef::TermFieldMatchData::invalidId());
            }
        }
    }
}

}
