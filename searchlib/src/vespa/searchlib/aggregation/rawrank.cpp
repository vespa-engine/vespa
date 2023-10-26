// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "rawrank.h"
#include <vespa/vespalib/util/sort.h>
#include <algorithm>

#if 0
namespace search {
namespace aggregation {

RawRank::RawRank(double rank)
    : _rank()
{
    _rank.resize(sizeof(double));
    vespalib::serializeForSort<vespalib::convertForSort<double, false> >(rank, &_rank[0]);
}

RawRank::RawRank(const char *buf, uint32_t len)
    : _rank(buf, buf + len)
{
}

int
RawRank::cmp(const RawRank &rhs) const
{
    uint32_t l = std::min(_rank.size(), rhs._rank.size());
    int diff = memcmp(&_rank[0], &rhs._rank[0], l);
    if (diff == 0) {
        diff = (_rank.size() - rhs._rank.size());
    }
    return diff;
}

vespalib::nbostream &
operator << (vespalib::nbostream &os, const RawRank &rr)
{
    return os << rr._rank;
}

vespalib::nbostream &
operator >> (vespalib::nbostream &is, RawRank &rr)
{
    return is >> rr._rank;
}

} // namespace search::aggregation
} // namespace search

#endif
// this function was added by ../../forcelink.sh
void forcelink_file_searchlib_aggregation_rawrank() {}
