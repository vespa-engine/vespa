// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstdint>

namespace search::bmcluster {

/*
 * Class containing bucket stats
 */
class BmBucketsStats
{
    uint64_t _buckets;
    uint64_t _buckets_pending;  // Buckets with pending ideal state operations
    bool     _valid;
    
public:
    BmBucketsStats();
    BmBucketsStats(uint64_t buckets, uint64_t buckets_pending, bool valid);
    ~BmBucketsStats();
    BmBucketsStats& operator+=(const BmBucketsStats& rhs);
    bool operator==(const BmBucketsStats &rhs) const;
    uint64_t get_buckets() const noexcept { return _buckets; }
    uint64_t get_buckets_pending() const noexcept { return _buckets_pending; }
    bool get_valid() const noexcept { return _valid; }
};

}
