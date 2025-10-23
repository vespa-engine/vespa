// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "andsearch.h"
#include "irequestcontext.h"
#include <vespa/searchlib/fef/matchdata.h>
#include <vespa/searchlib/fef/termfieldmatchdataarray.h>
#include <vespa/searchlib/fef/termfieldmatchdata.h>
#include <vespa/vespalib/util/vespa_dll_local.h>
#include <memory>
#include <vector>

namespace search::queryeval {

/**
 * Search iterator for a phrase, based on a set of child search iterators.
 */
class SimplePhraseSearch : public MultiSearch
{
    fef::MatchData::UP           _md;
    fef::TermFieldMatchDataArray _childMatch;
    std::vector<uint32_t>        _eval_order;
    fef::TermFieldMatchData     &_tmd;
    uint32_t                     _unpacked_docid;
    bool                         _strict;

    using It = fef::TermFieldMatchData::PositionsIterator;
    // Reuse this vector instead of allocating a new one when needed.
    std::vector<It> _iterators;

    VESPA_DLL_LOCAL void phraseSeek(uint32_t doc_id);
    VESPA_DLL_LOCAL void matchPhrase(uint32_t doc_id) __attribute__((noinline));
    VESPA_DLL_LOCAL void doStrictSeek(uint32_t doc_id) __attribute__((noinline));
public:
    /**
     * Takes ownership of the contents of children.
     * If this iterator is strict, the first child also needs to be strict.
     *
     * @param children SearchIterator objects for each child.
     * @param tmds TermFieldMatchData for the children.
     * @param eval_order determines the order of evaluation for the
     *                   terms. The term with fewest hits should be
     *                   evaluated first.
     **/
    SimplePhraseSearch(Children children,
                       fef::MatchData::UP md,
                       fef::TermFieldMatchDataArray childMatch,
                       std::vector<uint32_t> eval_order,
                       fef::TermFieldMatchData &tmd, bool strict);
    ~SimplePhraseSearch() override;
    void doSeek(uint32_t doc_id) override;
    void doUnpack(uint32_t doc_id) override;
    void initRange(uint32_t begin_id, uint32_t end_id) override;
    void visitMembers(vespalib::ObjectVisitor &visitor) const override;
    void get_element_ids(uint32_t docid, std::vector<uint32_t>& element_ids) override;
    void and_element_ids_into(uint32_t docid, std::vector<uint32_t>& element_ids) override;
    Trinary is_strict() const override { return (_strict ? Trinary::True : Trinary::False); }
};

}
