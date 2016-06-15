// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include "matchdatalayout.h"

namespace search {
namespace fef {

MatchDataLayout::MatchDataLayout()
    : _numTermFields(0),
      _numFeatures(0),
      _fieldIds(),
      _object_features()
{
}

MatchData::UP
MatchDataLayout::createMatchData() const
{
    MatchData::UP md(new MatchData(MatchData::params()
                                   .numTermFields(_numTermFields)
                                   .numFeatures(_numFeatures)));

    assert(_numTermFields == _fieldIds.size());
    for (size_t i = 0; i < _numTermFields; ++i) {
        md->resolveTermField(i)->setFieldId(_fieldIds[i]);
    }
    for (FeatureHandle object_handle: _object_features) {
        md->tag_feature_as_object(object_handle);
    }
    return md;
}

} // namespace fef
} // namespace search
