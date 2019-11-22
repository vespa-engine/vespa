// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "phrasesplitter.h"

namespace search {
namespace fef {

void
PhraseSplitter::considerTerm(uint32_t termIdx, const ITermData &term, std::vector<PhraseTerm> &phraseTerms, uint32_t fieldId)
{
    typedef search::fef::ITermFieldRangeAdapter FRA;

    for (FRA iter(term); iter.valid(); iter.next()) {
        if (iter.get().getFieldId() == fieldId) {
            TermFieldHandle h = iter.get().getHandle();
            _maxHandle = std::max(_maxHandle, h);
            if (term.getPhraseLength() > 1) {
                SimpleTermData prototype;
                prototype.setWeight(term.getWeight());
                prototype.setPhraseLength(1);
                prototype.setUniqueId(term.getUniqueId());
                prototype.addField(fieldId);
                phraseTerms.push_back(PhraseTerm(term, _terms.size(), h));
                for (uint32_t i = 0; i < term.getPhraseLength(); ++i) {
                    _terms.push_back(prototype);
                    _termIdxMap.push_back(TermIdx(_terms.size() - 1, true));
                }
                return;
            }
        }
    }
    _termIdxMap.push_back(TermIdx(termIdx, false));
}

PhraseSplitter::PhraseSplitter(const IQueryEnvironment & queryEnv,
                               uint32_t fieldId) :
    _queryEnv(queryEnv),
    _matchData(NULL),
    _terms(),
    _termMatches(),
    _termIdxMap(),
    _maxHandle(0),
    _skipHandles(0)
{
    TermFieldHandle numHandles = 0; // how many handles existed in underlying data
    std::vector<PhraseTerm> phraseTerms; // data about original phrase terms

    for (uint32_t i = 0; i < queryEnv.getNumTerms(); ++i) {
        const ITermData *td = queryEnv.getTerm(i);
        assert(td != NULL);
        considerTerm(i, *td, phraseTerms, fieldId);
        numHandles += td->numFields();
    }

    _skipHandles = _maxHandle + 1 + numHandles;
    for (uint32_t i = 0; i < _terms.size(); ++i) {
        // start at _skipHandles + 0
        _terms[i].field(0).setHandle(_skipHandles + _termMatches.size());
        TermFieldMatchData empty;
        empty.setFieldId(fieldId);
        _termMatches.push_back(empty);
    }

    for (uint32_t i = 0; i < phraseTerms.size(); ++i) {
        const PhraseTerm &pterm = phraseTerms[i];

        for (uint32_t j = 0; j < pterm.term.getPhraseLength(); ++j) {
            const ITermData &splitp_td = _terms[pterm.idx + j];
            const ITermFieldData& splitp_tfd = splitp_td.field(0);
            HowToCopy meta;
            meta.orig_handle = pterm.orig_handle;
            meta.split_handle = splitp_tfd.getHandle();
            meta.offsetInPhrase = j;
            _copyInfo.push_back(meta);
        }
    }
}

PhraseSplitter::~PhraseSplitter() {}

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
        assert(src != NULL && dst != NULL);
        copyTermFieldMatchData(*dst, *src, _copyInfo[i].offsetInPhrase);
    }

}

} // namespace fef
} // namespace search
