// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "multisearch.h"
#include <vespa/vespalib/util/priority_queue.h>
#include <vespa/searchlib/fef/matchdata.h>
#include <vespa/searchlib/fef/termfieldmatchdataarray.h>
#include <vespa/searchlib/attribute/iterator_pack.h>
#include <memory>
#include <vector>

namespace search {
namespace fef {
class TermFieldMatchData;
}  // namespace fef

namespace queryeval {

class Blueprint;

/**
 * Search iterator for a weighted set, based on a set of child search
 * iterators.
 */
class WeightedSetTermSearch : public SearchIterator
{
protected:
    WeightedSetTermSearch() {}

public:
    // TODO: pass ownership with unique_ptr
    static SearchIterator::UP create(const std::vector<SearchIterator *> &children,
                                     search::fef::TermFieldMatchData &tmd,
                                     bool field_is_filter,
                                     const std::vector<int32_t> &weights,
                                     fef::MatchData::UP match_data);

    static SearchIterator::UP create(search::fef::TermFieldMatchData &tmd,
                                     bool field_is_filter,
                                     const std::vector<int32_t> &weights,
                                     std::vector<DocumentWeightIterator> &&iterators);

    // used during docsum fetching to identify matching elements
    // initRange must be called before use.
    // doSeek/doUnpack must not be called.
    virtual void find_matching_elements(uint32_t docid, const std::vector<std::unique_ptr<Blueprint>> &child_blueprints, std::vector<uint32_t> &dst) = 0;
};

}  // namespace search::queryeval
}  // namespace search

