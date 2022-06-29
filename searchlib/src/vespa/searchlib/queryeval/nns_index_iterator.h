// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "searchiterator.h"
#include <vespa/searchlib/fef/termfieldmatchdata.h>
#include <vespa/searchlib/tensor/distance_function.h>
#include <vespa/searchlib/tensor/nearest_neighbor_index.h>

namespace search::queryeval {

class NnsIndexIterator : public SearchIterator
{
public:
    using Hit = search::tensor::NearestNeighborIndex::Neighbor;
    static std::unique_ptr<NnsIndexIterator> create(
            fef::TermFieldMatchData &tfmd,
            const std::vector<Hit> &hits,
            const search::tensor::DistanceFunction &dist_fun);
};

} // namespace
