// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "first_phase_rank_lookup.h"
#include <vespa/searchlib/fef/objectstore.h>
#include <cassert>
#include <limits>

using search::fef::AnyWrapper;

namespace search::features {

namespace {

const vespalib::string key = "firstPhaseRankLookup";

}

FirstPhaseRankLookup::FirstPhaseRankLookup()
    : _map()
{
}

FirstPhaseRankLookup::FirstPhaseRankLookup(FirstPhaseRankLookup&&) = default;

FirstPhaseRankLookup::~FirstPhaseRankLookup() = default;

feature_t
FirstPhaseRankLookup::lookup(uint32_t docid) const noexcept
{
    auto itr = _map.find(docid);
    if (itr != _map.end()) [[likely]] {
        return itr->second;
    } else {
        return std::numeric_limits<feature_t>::max();
    }
}

void
FirstPhaseRankLookup::add(uint32_t docid, uint32_t rank)
{
    auto insres = _map.insert(std::make_pair(docid, rank));
    assert(insres.second);
}

void
FirstPhaseRankLookup::make_shared_state(fef::IObjectStore& store)
{
    if (store.get(key) == nullptr) {
        store.add(key, std::make_unique<AnyWrapper<FirstPhaseRankLookup>>(FirstPhaseRankLookup()));
    }
}

FirstPhaseRankLookup*
FirstPhaseRankLookup::get_mutable_shared_state(fef::IObjectStore& store)
{
    auto* wrapper = dynamic_cast<AnyWrapper<FirstPhaseRankLookup>*>(store.get_mutable(key));
    return (wrapper == nullptr) ? nullptr :  &wrapper->getValue();
}

const FirstPhaseRankLookup*
FirstPhaseRankLookup::get_shared_state(const fef::IObjectStore& store)
{
    const auto* wrapper = dynamic_cast<const AnyWrapper<FirstPhaseRankLookup>*>(store.get(key));
    return (wrapper == nullptr) ? nullptr : &wrapper->getValue();
}

}
