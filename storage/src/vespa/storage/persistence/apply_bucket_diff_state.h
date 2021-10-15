// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/persistence/spi/bucket.h>
#include <vector>

namespace storage {

class ApplyBucketDiffEntryResult;
class MergeBucketInfoSyncer;

/*
 * State of all bucket diff entry spi operation (putAsync or removeAsync)
 * for one or more ApplyBucketDiffCommand / ApplyBucketDiffReply.
 */
class ApplyBucketDiffState {
    std::vector<ApplyBucketDiffEntryResult> _async_results;
    const MergeBucketInfoSyncer&            _merge_bucket_info_syncer;
    spi::Bucket                             _bucket;
    bool                                    _stale_bucket_info;
public:
    ApplyBucketDiffState(const MergeBucketInfoSyncer &merge_bucket_info_syncer, const spi::Bucket& bucket);
    ~ApplyBucketDiffState();
    void push_back(ApplyBucketDiffEntryResult&& result);
    bool empty() const;
    void wait();
    void check();
    void mark_stale_bucket_info();
    void sync_bucket_info();
};

}
