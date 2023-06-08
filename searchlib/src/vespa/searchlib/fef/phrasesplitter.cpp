// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "phrasesplitter.h"
#include "phrase_splitter_query_env.h"

namespace search::fef {

PhraseSplitter::PhraseSplitter(const PhraseSplitterQueryEnv& phrase_splitter_query_env)
    : _phrase_splitter_query_env(phrase_splitter_query_env),
      _skipHandles(_phrase_splitter_query_env.get_skip_handles()),
      _matchData(nullptr),
      _termMatches(_phrase_splitter_query_env.get_num_phrase_split_terms())
{
    uint32_t field_id = _phrase_splitter_query_env.get_field_id();
    for (auto & term_match : _termMatches) {
        term_match.setFieldId(field_id);
    }
    auto &phrase_terms = _phrase_splitter_query_env.get_phrase_terms();
    for (const auto &phrase_term : phrase_terms) {
        // Record that we need normal term field match data
        (void) phrase_term.term.lookupField(field_id)->getHandle(MatchDataDetails::Normal);
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
    for (const auto &copy_info : _phrase_splitter_query_env.get_copy_info()) {
        const TermFieldMatchData *src = _matchData->resolveTermField(copy_info.orig_handle);
        TermFieldMatchData *dst = resolveSplittedTermField(copy_info.split_handle);
        copyTermFieldMatchData(*dst, *src, copy_info.offsetInPhrase);
    }

}

}
