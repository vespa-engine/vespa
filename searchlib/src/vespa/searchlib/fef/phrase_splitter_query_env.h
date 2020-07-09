// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "iqueryenvironment.h"
#include "simpletermdata.h"

namespace search::fef {

/**
 * This class is used together with PhraseSplitter to split all phrase
 * terms in a query environment into separate terms. New TermData
 * objects are created for each splitted phrase term and managed by
 * this class.  Unmodified single terms are served from the query
 * environment and match data.
 *
 * Use this class and PhraseSplitter class if you want to handle a
 * phrase term the same way as single terms.
 **/
class PhraseSplitterQueryEnv : public IQueryEnvironment
{
private:
    struct TermIdx {
        uint32_t idx;      // index into either query environment or vector of TermData objects
        bool     splitted; // whether this term has been splitted or not
        TermIdx(uint32_t i, bool s) : idx(i), splitted(s) {}
    };
public:
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
private:
    const IQueryEnvironment        &_queryEnv;
    std::vector<SimpleTermData>     _terms;       // splitted terms
    std::vector<HowToCopy>          _copyInfo;
    std::vector<TermIdx>            _termIdxMap;  // renumbering of terms
    TermFieldHandle                 _maxHandle;   // the largest among original term field handles
    TermFieldHandle                 _skipHandles;   // how many handles to skip
    uint32_t                        _field_id;
    std::vector<PhraseTerm>         _phrase_terms; // data about original phrase terms

    void considerTerm(uint32_t termIdx, const ITermData &term, uint32_t fieldId);

public:
    /**
     * Create a phrase splitter based on the given query environment.
     *
     * @param queryEnv the query environment to wrap.
     * @param fieldId the field where we need to split phrases
     **/
    PhraseSplitterQueryEnv(const IQueryEnvironment & queryEnv, uint32_t fieldId);
    ~PhraseSplitterQueryEnv();

    /**
     * Update the underlying TermFieldMatchData objects based on the bound MatchData object.
     **/
    uint32_t getNumTerms() const override { return _termIdxMap.size(); }

    const ITermData * getTerm(uint32_t idx) const override {
        if (idx >= _termIdxMap.size()) {
            return nullptr;
        }
        const TermIdx & ti = _termIdxMap[idx];
        return ti.splitted ? &_terms[ti.idx] : _queryEnv.getTerm(ti.idx);
    }

    const Properties & getProperties() const override { return _queryEnv.getProperties(); }
 // const Location & getLocation() const override { return _queryEnv.getLocation(); }
    std::vector<const Location *> getAllLocations() const override {
        return _queryEnv.getAllLocations();
    }
    const attribute::IAttributeContext & getAttributeContext() const override { return _queryEnv.getAttributeContext(); }
    double get_average_field_length(const vespalib::string &field_name) const override { return _queryEnv.get_average_field_length(field_name); }
    const IIndexEnvironment & getIndexEnvironment() const override { return _queryEnv.getIndexEnvironment(); }

    // Accessor methods used by PhraseSplitter
    TermFieldHandle get_skip_handles() const { return _skipHandles; }
    uint32_t get_num_phrase_split_terms() const { return _terms.size(); }
    uint32_t get_field_id() const { return _field_id; }
    const std::vector<HowToCopy>& get_copy_info() const { return _copyInfo; }
    const std::vector<PhraseTerm>& get_phrase_terms() const { return _phrase_terms; }
};


}
