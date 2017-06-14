// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "matchdatalayout.h"

namespace search {
namespace fef {

MatchDataLayout::MatchDataLayout()
    : _numTermFields(0),
      _fieldIds()
{
}

MatchDataLayout::~MatchDataLayout() { }


MatchData::UP
MatchDataLayout::createMatchData() const
{
    MatchData::UP md(new MatchData(MatchData::params()
                                   .numTermFields(_numTermFields)));
    assert(_numTermFields == _fieldIds.size());
    for (size_t i = 0; i < _numTermFields; ++i) {
        md->resolveTermField(i)->setFieldId(_fieldIds[i]);
    }
    return md;
}

} // namespace fef
} // namespace search
