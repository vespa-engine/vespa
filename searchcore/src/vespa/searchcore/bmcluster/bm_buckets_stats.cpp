// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "bm_buckets_stats.h"

namespace search::bmcluster {

BmBucketsStats::BmBucketsStats()
    : BmBucketsStats(0u, 0u, false)
{
}

BmBucketsStats::BmBucketsStats(uint64_t buckets, uint64_t buckets_pending, bool valid)
    : _buckets(buckets),
      _buckets_pending(buckets_pending),
      _valid(valid)
{
}

BmBucketsStats::~BmBucketsStats() = default;

BmBucketsStats&
BmBucketsStats::operator+=(const BmBucketsStats& rhs)
{
    _valid &= rhs._valid;
    _buckets += rhs._buckets;
    _buckets_pending += rhs._buckets_pending;
    return *this;
}

bool
BmBucketsStats::operator==(const BmBucketsStats &rhs) const
{
    return ((_buckets == rhs._buckets) &&
            (_buckets_pending == rhs._buckets_pending) &&
            (_valid == rhs._valid));
}

}
