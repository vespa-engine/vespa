// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

namespace storage::spi { class Bucket; }

namespace storage {

/*
 * Interface class for syncing bucket info during merge.
 */
class MergeBucketInfoSyncer {
public:
    virtual ~MergeBucketInfoSyncer() = default;
    virtual void sync_bucket_info(const spi::Bucket& bucket) const = 0;
};

}
