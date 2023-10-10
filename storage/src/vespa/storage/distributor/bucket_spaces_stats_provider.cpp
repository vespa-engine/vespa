// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "bucket_spaces_stats_provider.h"

namespace storage::distributor {

std::ostream&
operator<<(std::ostream& out, const BucketSpaceStats& stats)
{
    out << "{valid=" << stats.valid() << ", bucketsTotal=" << stats.bucketsTotal() << ", bucketsPending=" << stats.bucketsPending() << "}";
    return out;
}

void
merge_bucket_spaces_stats(BucketSpacesStatsProvider::BucketSpacesStats& dest,
                          const BucketSpacesStatsProvider::BucketSpacesStats& src)
{
    for (const auto& entry : src) {
        const auto& bucket_space_name = entry.first;
        auto itr = dest.find(bucket_space_name);
        if (itr != dest.end()) {
            itr->second.merge(entry.second);
        } else {
            // We need to explicitly handle this case to avoid creating an empty BucketSpaceStats that is not valid.
            dest[bucket_space_name] = entry.second;
        }
    }
}

void
merge_per_node_bucket_spaces_stats(BucketSpacesStatsProvider::PerNodeBucketSpacesStats& dest,
                                   const BucketSpacesStatsProvider::PerNodeBucketSpacesStats& src)
{
    for (const auto& entry : src) {
        auto node_index = entry.first;
        merge_bucket_spaces_stats(dest[node_index], entry.second);
    }
}

}
