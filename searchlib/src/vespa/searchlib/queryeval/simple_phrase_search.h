// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "andsearch.h"
#include "irequestcontext.h"
#include <vespa/searchlib/fef/matchdata.h>
#include <vespa/searchlib/fef/termfieldmatchdataarray.h>
#include <vespa/searchlib/fef/termfieldmatchdata.h>
#include <memory>
#include <vector>

namespace search::queryeval {

/**
 * Search iterator for a phrase, based on a set of child search iterators.
 */
class SimplePhraseSearch : public AndSearch
{
    fef::MatchData::UP           _md;
    fef::TermFieldMatchDataArray _childMatch;
    std::vector<uint32_t>        _eval_order;
    fef::TermFieldMatchData     &_tmd;
    bool                         _strict;

    typedef fef::TermFieldMatchData::PositionsIterator It;
    // Reuse this vector instead of allocating a new one when needed.
    std::vector<It> _iterators;

    void phraseSeek(uint32_t doc_id);
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
    void doSeek(uint32_t doc_id) override;
    void doUnpack(uint32_t doc_id) override;
    void visitMembers(vespalib::ObjectVisitor &visitor) const override;
};

}
