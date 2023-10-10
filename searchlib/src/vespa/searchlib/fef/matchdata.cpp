// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "matchdata.h"

namespace search {
namespace fef {

MatchData::MatchData(const Params &cparams)
    : _termFields(cparams.numTermFields()),
      _termwise_limit(1.0)
{
}

void
MatchData::soft_reset()
{
    for (auto &tfmd: _termFields) {
        tfmd.resetOnlyDocId(TermFieldMatchData::invalidId());
    }
    _termwise_limit = 1.0;
}

MatchData::UP
MatchData::makeTestInstance(uint32_t numTermFields, uint32_t fieldIdLimit)
{
    MatchData::UP data(new MatchData(Params().numTermFields(numTermFields)));
    for (uint32_t i = 0; i < numTermFields; ++i) {
        data->resolveTermField(i)->setFieldId(i % fieldIdLimit);
    }
    return data;
}

} // namespace fef
} // namespace search
