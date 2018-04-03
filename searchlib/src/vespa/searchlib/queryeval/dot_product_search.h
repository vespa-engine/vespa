// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "multisearch.h"
#include <vespa/vespalib/util/priority_queue.h>
#include <vespa/searchlib/fef/matchdata.h>
#include <vespa/searchlib/fef/termfieldmatchdataarray.h>
#include <vespa/searchlib/attribute/iterator_pack.h>

namespace search::fef { class TermFieldMatchData; }

namespace search::queryeval {

/**
 * Search iterator for a sparse dot product, based on a set of child
 * search iterators.
 *
 * This class is a base class for a set of different instantiations of
 * DotProductSearchImpl, defined in the .cpp-file.
 */
class DotProductSearch : public SearchIterator
{
protected:
    DotProductSearch() {}

public:
    static SearchIterator::UP create(const std::vector<SearchIterator*> &children,
                                     search::fef::TermFieldMatchData &tmd,
                                     const std::vector<fef::TermFieldMatchData*> &childMatch,
                                     const std::vector<int32_t> &weights,
                                     fef::MatchData::UP md);

    static SearchIterator::UP create(search::fef::TermFieldMatchData &tmd,
                                     const std::vector<int32_t> &weights,
                                     std::vector<DocumentWeightIterator> &&iterators);
};

}

