// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

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

/**
 * Search iterator for a weighted set, based on a set of child search
 * iterators.
 */
class WeightedSetTermSearch : public SearchIterator
{
protected:
    WeightedSetTermSearch() {}

public:
    static SearchIterator* create(const std::vector<SearchIterator*> &children,
                                  search::fef::TermFieldMatchData &tmd,
                                  const std::vector<int32_t> &weights);

    static SearchIterator::UP create(search::fef::TermFieldMatchData &tmd,
                                     const std::vector<int32_t> &weights,
                                     std::vector<DocumentWeightIterator> &&iterators);
};

}  // namespace search::queryeval
}  // namespace search

