// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/objects/nbostream.h>
#include <vector>

namespace search {
namespace aggregation {

#if 0
/**
 * Thin wrapper around a rank value represented as a sequence of
 * bytes.
 **/
class RawRank
{
private:
    std::vector<uint8_t> _rank;

public:
    RawRank() : _rank() {}
    RawRank(double rank);
    RawRank(const char *buf, uint32_t len);
    int cmp(const RawRank &rhs) const;
    const std::vector<uint8_t> &getRank() const { return _rank; }
    friend vespalib::nbostream &operator << (vespalib::nbostream &os, const RawRank &rr);
    friend vespalib::nbostream &operator >> (vespalib::nbostream &is, RawRank &rr);
};
#else
typedef double RawRank;
#endif

} // namespace search::aggregation
} // namespace search

