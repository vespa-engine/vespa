// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "phrasesplitter.h"

namespace search::fef {

PhraseSplitter::PhraseSplitter(const IQueryEnvironment & queryEnv, uint32_t fieldId)
    : PhraseSplitterQueryEnv(queryEnv, fieldId),
      _matchData(nullptr),
      _termMatches()
{
    _termMatches.reserve(_terms.size());
    for ([[maybe_unused]] auto & term : _terms) {
        _termMatches.emplace_back();
        _termMatches.back().setFieldId(fieldId);
    }
}

PhraseSplitter::~PhraseSplitter() = default;

void
PhraseSplitter::copyTermFieldMatchData(TermFieldMatchData & dst, const TermFieldMatchData & src, uint32_t hitOffset)
{
    dst.reset(src.getDocId());

    for (TermFieldMatchData::PositionsIterator itr = src.begin(), end = src.end(); itr != end; ++itr) {
        TermFieldMatchDataPosition pos(*itr);
        pos.setPosition(pos.getPosition() + hitOffset);
        dst.appendPosition(TermFieldMatchDataPosition(pos));
    }
}

void
PhraseSplitter::update()
{
    for (uint32_t i = 0; i < _copyInfo.size(); ++i) {
        const TermFieldMatchData *src = _matchData->resolveTermField(_copyInfo[i].orig_handle);
        TermFieldMatchData *dst = resolveSplittedTermField(_copyInfo[i].split_handle);
        assert(src != nullptr && dst != nullptr);
        copyTermFieldMatchData(*dst, *src, _copyInfo[i].offsetInPhrase);
    }

}

}
