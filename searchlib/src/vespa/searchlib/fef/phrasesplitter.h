// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "iqueryenvironment.h"
#include "matchdata.h"
#include "simpletermdata.h"
#include "termfieldmatchdata.h"
#include "fieldinfo.h"

namespace search {
namespace fef {

/**
 * This class is used to split all phrase terms in a query environment
 * into separate terms. New TermData and TermFieldMatchData objects
 * are created for each splitted phrase term and managed by this
 * class.  Unmodified single terms are served from the query
 * environment and match data.
 *
 * The TermFieldMatchData objects managed by this class are updated
 * based on the TermFieldMatchData objects associated with the
 * original phrase terms. Positions are adjusted with +1 for each term
 * after the first one.
 *
 * Use this class if you want to handle a phrase term the same way as
 * single terms.
 **/
class PhraseSplitter : public IQueryEnvironment
{
private:
    struct TermIdx {
        uint32_t idx;      // index into either query environment or vector of TermData objects
        bool     splitted; // whether this term has been splitted or not
        TermIdx(uint32_t i, bool s) : idx(i), splitted(s) {}
    };
    struct PhraseTerm {
        const ITermData & term; // for original phrase
        uint32_t idx; // index into vector of our TermData objects
        TermFieldHandle orig_handle;
        PhraseTerm(const ITermData & t, uint32_t i, uint32_t h) : term(t), idx(i), orig_handle(h) {}
    };
    struct HowToCopy {
        TermFieldHandle orig_handle;
        TermFieldHandle split_handle;
        uint32_t offsetInPhrase;
    };

    const IQueryEnvironment        &_queryEnv;
    const MatchData                *_matchData;
    std::vector<SimpleTermData>     _terms;       // splitted terms
    std::vector<TermFieldMatchData> _termMatches; // match objects associated with splitted terms
    std::vector<HowToCopy>          _copyInfo;
    std::vector<TermIdx>            _termIdxMap;  // renumbering of terms
    TermFieldHandle                 _maxHandle;   // the largest among original term field handles
    TermFieldHandle                 _skipHandles;   // how many handles to skip

    void considerTerm(uint32_t termIdx, const ITermData &term, std::vector<PhraseTerm> &phraseTerms, uint32_t fieldId);
    void splitPhrase(const ITermData &phrase, std::vector<PhraseTerm> &phraseTerms, uint32_t fieldId);

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
    PhraseSplitter(const IQueryEnvironment & queryEnv, uint32_t fieldId);
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
    uint32_t getNumTerms() const override { return _termIdxMap.size(); }

    const ITermData * getTerm(uint32_t idx) const override {
        if (idx >= _termIdxMap.size()) {
            return NULL;
        }
        const TermIdx & ti = _termIdxMap[idx];
        return ti.splitted ? &_terms[ti.idx] : _queryEnv.getTerm(ti.idx);
    }

    /**
     * Inherit doc from MatchData.
     **/
    const TermFieldMatchData * resolveTermField(TermFieldHandle handle) const {
        if (_matchData == NULL) {
            return NULL;
        }
        return handle < _skipHandles ? _matchData->resolveTermField(handle) : resolveSplittedTermField(handle);
    }

    const Properties & getProperties() const override { return _queryEnv.getProperties(); }
    const Location & getLocation() const override { return _queryEnv.getLocation(); }
    const attribute::IAttributeContext & getAttributeContext() const override { return _queryEnv.getAttributeContext(); }
    const IIndexEnvironment & getIndexEnvironment() const override { return _queryEnv.getIndexEnvironment(); }
    void bind_match_data(const fef::MatchData &md) { _matchData = &md; }
};

} // namespace fef
} // namespace search

