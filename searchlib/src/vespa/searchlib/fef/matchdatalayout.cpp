// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "matchdatalayout.h"

#include <vespa/vespalib/util/backtrace.h>

#include <cassert>

namespace search::fef {

MatchDataLayout::MatchDataLayout() : _fieldIds() {
}

MatchDataLayout::~MatchDataLayout() = default;

MatchData::UP MatchDataLayout::createMatchData() const {
    vespalib::tdl::Layout<MatchDataDomain, MatchData> layout;
    auto ah = layout.reserve_array<TermFieldMatchData>(_fieldIds.size());
    auto md = layout.create_data();
    auto array = md->resolve_array<TermFieldMatchData>(ah);
    assert(array.size() == _fieldIds.size());
    for (size_t i = 0; i < _fieldIds.size(); ++i) {
        array[i].setFieldId(_fieldIds[i]);
    }
    return md;
}

} // namespace search::fef
