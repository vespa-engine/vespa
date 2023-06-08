// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "matchdatalayout.h"
#include <cassert>

namespace search::fef {

MatchDataLayout::MatchDataLayout()
    : _fieldIds()
{
}

MatchDataLayout::~MatchDataLayout() = default;


MatchData::UP
MatchDataLayout::createMatchData() const
{
    auto md = std::make_unique<MatchData>(MatchData::params().numTermFields(_fieldIds.size()));
    for (size_t i = 0; i < _fieldIds.size(); ++i) {
        md->resolveTermField(i)->setFieldId(_fieldIds[i]);
    }
    return md;
}

}
