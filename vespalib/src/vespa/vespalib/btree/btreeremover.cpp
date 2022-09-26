// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "btreeremover.hpp"
#include "btreenode.hpp"

namespace vespalib::btree {

template class BTreeRemover<uint32_t, uint32_t, NoAggregated>;
template class BTreeRemover<uint32_t, BTreeNoLeafData, NoAggregated>;
template class BTreeRemover<uint32_t, int32_t, MinMaxAggregated,
                            std::less<uint32_t>, BTreeDefaultTraits, MinMaxAggrCalc>;

}
