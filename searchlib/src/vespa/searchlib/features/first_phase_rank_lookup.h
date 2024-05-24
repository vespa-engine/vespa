// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/common/feature.h>
#include <vespa/vespalib/stllike/hash_map.h>

namespace search::fef { class IObjectStore; }

namespace search::features {

/*
 * This class contains a mapping from docids used by second phase to
 * first phase rank.
 */
class FirstPhaseRankLookup {
    vespalib::hash_map<uint32_t, uint32_t> _map;
public:
    FirstPhaseRankLookup();
    FirstPhaseRankLookup(const FirstPhaseRankLookup&) = delete;
    FirstPhaseRankLookup(FirstPhaseRankLookup&&);
    ~FirstPhaseRankLookup();
    FirstPhaseRankLookup& operator=(const FirstPhaseRankLookup&) = delete;
    FirstPhaseRankLookup& operator=(FirstPhaseRankLookup&&) = delete;
    feature_t lookup(uint32_t docid) const noexcept;
    void add(uint32_t docid, uint32_t rank);
    static void make_shared_state(fef::IObjectStore& store);
    static FirstPhaseRankLookup* get_mutable_shared_state(fef::IObjectStore& store);
    static const FirstPhaseRankLookup* get_shared_state(const fef::IObjectStore& store);
};

}
