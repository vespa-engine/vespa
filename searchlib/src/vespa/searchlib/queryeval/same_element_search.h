// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/fef/matchdata.h>
#include <vespa/searchlib/fef/termfieldmatchdataarray.h>
#include <vespa/searchlib/fef/termfieldmatchdata.h>
#include <vespa/searchlib/common/matching_elements.h>
#include <vespa/searchlib/queryeval/searchiterator.h>
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

    fef::TermFieldMatchData&                     _tfmd;
    std::vector<fef::TermFieldMatchData*>        _descendants_index_tfmd;
    std::vector<std::unique_ptr<SearchIterator>> _children;
    std::vector<uint32_t>                        _matchingElements;
    bool                                         _strict;

    void fetch_matching_elements(uint32_t docid, std::vector<uint32_t> &dst);
    bool check_docid_match(uint32_t docid);
    bool check_element_match(uint32_t docid);

public:
    SameElementSearch(fef::TermFieldMatchData &tfmd,
                      std::vector<fef::TermFieldMatchData*> descendants_index_tfmd,
                      std::vector<std::unique_ptr<SearchIterator>> children,
                      bool strict);
    ~SameElementSearch() override;
    void initRange(uint32_t begin_id, uint32_t end_id) override;
    void doSeek(uint32_t docid) override;
    void doUnpack(uint32_t docid) override;
    void visitMembers(vespalib::ObjectVisitor &visitor) const override;
    const std::vector<std::unique_ptr<SearchIterator>> &children() const { return _children; }

    // used during docsum fetching to identify matching elements
    // initRange must be called before use.
    // doSeek/doUnpack must not be called.
    void find_matching_elements(uint32_t docid, std::vector<uint32_t> &dst);
};

}
