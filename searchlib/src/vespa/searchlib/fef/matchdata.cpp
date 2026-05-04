// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "matchdata.h"
#include "matchdatalayout.h"

namespace search {
namespace fef {

MatchData::MatchData(vespalib::tdl::DataKey key) : MatchDataBase(key), _termwise_limit(1.0) {
}

void MatchData::soft_reset() {
    for (auto& tfmd : all_of<TermFieldMatchData>()) {
        tfmd.resetOnlyDocId(TermFieldMatchData::invalidId());
    }
    _termwise_limit = 1.0;
}

MatchData::UP MatchData::makeTestInstance(uint32_t numTermFields, uint32_t fieldIdLimit) {
    MatchDataLayout layout;
    for (uint32_t i = 0; i < numTermFields; ++i) {
        layout.allocTermField(i % fieldIdLimit);
    }
    return layout.createMatchData();
}

} // namespace fef
} // namespace search
