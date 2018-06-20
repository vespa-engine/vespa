// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "searchiterator.h"
#include <vespa/searchlib/fef/matchdata.h>
#include <vespa/searchlib/fef/termfieldmatchdataarray.h>
#include <vespa/searchlib/fef/termfieldmatchdata.h>
#include <memory>
#include <vector>

namespace search::queryeval {

/**
 * Search iterator for a collection of terms that need to match within
 * the same element (array index).
 */
class SameElementSearch : public SearchIterator
{
private:
    using It = fef::TermFieldMatchData::PositionsIterator;

    fef::MatchData::UP              _md;
    std::vector<SearchIterator::UP> _children;
    fef::TermFieldMatchDataArray    _childMatch;
    std::vector<It>                 _iterators;
    bool                            _strict;

    void unpack_children(uint32_t docid);
    bool check_docid_match(uint32_t docid);
    bool check_element_match(uint32_t docid);

public:
    SameElementSearch(fef::MatchData::UP md,
                      std::vector<SearchIterator::UP> children,
                      const fef::TermFieldMatchDataArray &childMatch,
                      bool strict);
    void initRange(uint32_t begin_id, uint32_t end_id) override;
    void doSeek(uint32_t docid) override;
    void doUnpack(uint32_t) override {}
    void visitMembers(vespalib::ObjectVisitor &visitor) const override;
    const std::vector<SearchIterator::UP> &children() const { return _children; }
};

}
