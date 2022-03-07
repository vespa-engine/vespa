// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <memory>

namespace storage::spi { class Bucket; }

namespace storage {

class ApplyBucketDiffState;

/*
 * Interface class for syncing bucket info during merge.
 */
class MergeBucketInfoSyncer {
public:
    virtual ~MergeBucketInfoSyncer() = default;
    virtual void sync_bucket_info(const spi::Bucket& bucket) const = 0;
    virtual void schedule_delayed_delete(std::unique_ptr<ApplyBucketDiffState> state) const = 0;
};

}
