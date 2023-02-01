// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "phrase_splitter_query_env.h"

namespace search::fef {

void
PhraseSplitterQueryEnv::considerTerm(uint32_t termIdx, const ITermData &term, uint32_t fieldId)
{
    using FRA = search::fef::ITermFieldRangeAdapter;

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
                _phrase_terms.push_back(PhraseTerm(term, _terms.size(), h));
                for (uint32_t i = 0; i < term.getPhraseLength(); ++i) {
                    _terms.emplace_back(prototype);
                    _termIdxMap.push_back(TermIdx(_terms.size() - 1, true));
                }
                return;
            }
        }
    }
    _termIdxMap.push_back(TermIdx(termIdx, false));
}

PhraseSplitterQueryEnv::PhraseSplitterQueryEnv(const IQueryEnvironment & queryEnv, uint32_t fieldId)
    : _queryEnv(queryEnv),
      _terms(),
      _termIdxMap(),
      _maxHandle(0),
      _skipHandles(0),
      _field_id(fieldId),
      _phrase_terms()
{
    TermFieldHandle numHandles = 0; // how many handles existed in underlying data
    for (uint32_t i = 0; i < queryEnv.getNumTerms(); ++i) {
        const ITermData *td = queryEnv.getTerm(i);
        assert(td != nullptr);
        considerTerm(i, *td, fieldId);
        numHandles += td->numFields();
    }

    _skipHandles = _maxHandle + 1 + numHandles;
    TermFieldHandle term_handle = _skipHandles;
    for (auto & term : _terms) {
        // start at _skipHandles + 0
        term.field(0).setHandle(term_handle);
        ++term_handle;
    }

    for (uint32_t i = 0; i < _phrase_terms.size(); ++i) {
        const PhraseTerm &pterm = _phrase_terms[i];

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

PhraseSplitterQueryEnv::~PhraseSplitterQueryEnv() = default;

}
