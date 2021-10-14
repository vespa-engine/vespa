// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "matchdatalayout.h"
#include <cassert>

namespace search::fef {

MatchDataLayout::MatchDataLayout()
    : _numTermFields(0),
      _fieldIds()
{
}

MatchDataLayout::~MatchDataLayout() = default;


MatchData::UP
MatchDataLayout::createMatchData() const
{
    assert(_numTermFields == _fieldIds.size());
    auto md = std::make_unique<MatchData>(MatchData::params().numTermFields(_numTermFields));
    for (size_t i = 0; i < _numTermFields; ++i) {
        md->resolveTermField(i)->setFieldId(_fieldIds[i]);
    }
    return md;
}

}
