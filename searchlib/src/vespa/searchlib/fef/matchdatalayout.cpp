// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "matchdatalayout.h"
#include <cassert>

#include <vespa/vespalib/util/backtrace.h>

namespace search::fef {

MatchDataLayout::MatchDataLayout()
    : _fieldIds()
{
    // auto backtrace = vespalib::getStackTrace(0);
    // fprintf(stderr, "created MDL %p from:%s\n", this, backtrace.c_str());
}

MatchDataLayout::~MatchDataLayout() = default;


MatchData::UP
MatchDataLayout::createMatchData() const
{
    auto md = std::make_unique<MatchData>(MatchData::params().numTermFields(_fieldIds.size()));
    for (size_t i = 0; i < _fieldIds.size(); ++i) {
        md->resolveTermField(i)->setFieldId(_fieldIds[i]);
    }
    // fprintf(stderr, "MDL %p creating MD %p size %zd\n", this, md.get(), _fieldIds.size());
    return md;
}

}
