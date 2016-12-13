// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include "matchdata.h"

namespace search {
namespace fef {

MatchData::MatchData(const Params &cparams)
    : _termFields(cparams.numTermFields()),
      _features(cparams.numFeatures()),
      _feature_is_object(cparams.numFeatures(), false),
      _termwise_limit(1.0)
{
}

MatchData::UP
MatchData::makeTestInstance(uint32_t numFeatures, uint32_t numHandles, uint32_t fieldIdLimit)
{
    MatchData::UP data(new MatchData(Params().numFeatures(numFeatures).numTermFields(numHandles)));
    for (uint32_t i = 0; i < numHandles; ++i) {
        data->resolveTermField(i)->setFieldId(i % fieldIdLimit);
    }
    return data;
}

} // namespace fef
} // namespace search
