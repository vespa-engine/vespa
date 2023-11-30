// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "multisearch.h"
#include <vespa/vespalib/util/priority_queue.h>
#include <vespa/searchlib/fef/matchdata.h>
#include <vespa/searchlib/fef/termfieldmatchdataarray.h>
#include <vespa/searchlib/attribute/posting_iterator_pack.h>
#include <memory>
#include <variant>
#include <vector>

namespace search::fef { class TermFieldMatchData; }

namespace search::queryeval {

class Blueprint;

/**
 * Search iterator for a weighted set, based on a set of child search
 * iterators.
 */
class WeightedSetTermSearch : public SearchIterator
{
protected:
    WeightedSetTermSearch() = default;

public:
    // TODO: pass ownership with unique_ptr
    static SearchIterator::UP create(const std::vector<SearchIterator *> &children,
                                     search::fef::TermFieldMatchData &tmd,
                                     bool field_is_filter,
                                     const std::vector<int32_t> &weights,
                                     fef::MatchData::UP match_data);

    static SearchIterator::UP create(search::fef::TermFieldMatchData &tmd,
                                     bool field_is_filter,
                                     std::variant<std::reference_wrapper<const std::vector<int32_t>>, std::vector<int32_t>> weights,
                                     std::vector<DocidWithWeightIterator> &&iterators);

    // used during docsum fetching to identify matching elements
    // initRange must be called before use.
    // doSeek/doUnpack must not be called.
    virtual void find_matching_elements(uint32_t docid, const std::vector<std::unique_ptr<Blueprint>> &child_blueprints, std::vector<uint32_t> &dst) = 0;
};

}
