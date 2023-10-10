// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "matchdata.h"
#include "termfieldmatchdata.h"

namespace search::fef {

class PhraseSplitterQueryEnv;

/**
 * This class is used together with PhraseSplitterQueryEnv to split
 * all phrase terms in a query environment into separate terms. New
 * TermFieldMatchData objects are created for each splitted phrase
 * term and managed by this class.  Unmodified single terms are served
 * from the query environment and match data.
 *
 * The TermFieldMatchData objects managed by this class are updated
 * based on the TermFieldMatchData objects associated with the
 * original phrase terms. Positions are adjusted with +1 for each term
 * after the first one.
 *
 * Use this class and PhraseSplitterQueryEnv if you want to handle a
 * phrase term the same way as single terms.
 **/
class PhraseSplitter
{
    const PhraseSplitterQueryEnv&   _phrase_splitter_query_env;
    TermFieldHandle                 _skipHandles;
    const MatchData                *_matchData;
    std::vector<TermFieldMatchData> _termMatches; // match objects associated with splitted terms

    TermFieldMatchData *resolveSplittedTermField(TermFieldHandle handle) {
        return &_termMatches[handle - _skipHandles];
    }

    const TermFieldMatchData *resolveSplittedTermField(TermFieldHandle handle) const {
        return &_termMatches[handle - _skipHandles];
    }

public:
    /**
     * Create a phrase splitter based on the given query environment.
     *
     * @param queryEnv the query environment to wrap.
     * @param field the field where we need to split phrases
     **/
    PhraseSplitter(const PhraseSplitterQueryEnv &phrase_splitter_query_env);
    ~PhraseSplitter();

    /**
     * Copy the source object to the destination object.
     * Use the given hit offset when copying position information. pos (x) -> pos (x + hitOffset).
     *
     * @param dst the destination object.
     * @param src the source object.
     * @param hitOffset the offset to use when copying position information.
     **/
    static void copyTermFieldMatchData(TermFieldMatchData & dst, const TermFieldMatchData & src, uint32_t hitOffset);

    /**
     * Update the underlying TermFieldMatchData objects based on the bound MatchData object.
     **/
    void update();

    /**
     * Inherit doc from MatchData.
     **/
    const TermFieldMatchData * resolveTermField(TermFieldHandle handle) const {
        if (_matchData == nullptr) {
            return nullptr;
        }
        return handle < _skipHandles ? _matchData->resolveTermField(handle) : resolveSplittedTermField(handle);
    }

    void bind_match_data(const fef::MatchData &md) { _matchData = &md; }
    const PhraseSplitterQueryEnv& get_query_env() const { return _phrase_splitter_query_env; }
};

}
