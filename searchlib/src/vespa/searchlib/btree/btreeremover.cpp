// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include "btreeremover.h"
#include "btreenodeallocator.h"
#include "btreerootbase.hpp"
#include "btreeremover.hpp"
#include "btreenode.hpp"

namespace search
{

namespace btree
{

template class BTreeRemover<uint32_t, uint32_t, NoAggregated>;
template class BTreeRemover<uint32_t, BTreeNoLeafData, NoAggregated>;
template class BTreeRemover<uint32_t, int32_t, MinMaxAggregated,
                            std::less<uint32_t>,
                            BTreeDefaultTraits,
                            MinMaxAggrCalc>;

} // namespace btree

} // namespace search
