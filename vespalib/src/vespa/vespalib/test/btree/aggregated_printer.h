// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/btree/noaggregated.h>
#include <vespa/vespalib/btree/minmaxaggregated.h>

namespace vespalib::btree::test {

template <typename ostream, typename Aggregated>
void printAggregated(ostream &os, const Aggregated &aggr);

template <typename ostream>
void printAggregated(ostream &os, const NoAggregated &aggr)
{
    (void) os;
    (void) aggr;
}

template <typename ostream>
void printAggregated(ostream &os, const MinMaxAggregated &aggr)
{
    os << "[min=" << aggr.getMin() << ",max=" << aggr.getMax() << "]";
}

} // namespace vespalib::btree::test
