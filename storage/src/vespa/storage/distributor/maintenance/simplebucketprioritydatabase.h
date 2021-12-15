// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "bucketprioritydatabase.h"
#include <vespa/vespalib/stllike/hash_map.h>
#include <set>
#include <map>

namespace storage::distributor {

class SimpleBucketPriorityDatabase : public BucketPriorityDatabase
{
public:
    SimpleBucketPriorityDatabase();
    ~SimpleBucketPriorityDatabase() override;
    using Priority = PrioritizedBucket::Priority;

    void setPriority(const PrioritizedBucket&) override;
    const_iterator begin() const override;
    const_iterator end() const override;

    std::string toString() const;

private:
    struct PriFifoCompositeKey {
        Priority _pri;
        uint64_t _seq_num;

        constexpr PriFifoCompositeKey() noexcept : _pri(Priority::VERY_LOW), _seq_num(0) {}
        constexpr PriFifoCompositeKey(Priority pri, uint64_t seq_num) noexcept
            : _pri(pri),
              _seq_num(seq_num)
        {}

        constexpr bool operator<(const PriFifoCompositeKey& rhs) const noexcept {
            if (_pri != rhs._pri) {
                // Unlike StorageAPI priorities, MaintenancePriority is higher value == higher priority
                return (_pri > rhs._pri);
            }
            return _seq_num < rhs._seq_num;
        }
    };

    using PriFifoBucketMap = std::map<PriFifoCompositeKey, document::Bucket>;
    // Important: the map iterator instances MUST be stable in the presence of other inserts/erases!
    using BucketToPriIteratorMap = vespalib::hash_map<document::Bucket, PriFifoBucketMap::iterator, document::Bucket::hash>;

    class PriFifoMappingConstIteratorImpl final : public ConstIteratorImpl {
        PriFifoBucketMap::const_iterator _pri_fifo_iter;
        PriFifoBucketMap::const_iterator _pri_fifo_end;
    public:
        PriFifoMappingConstIteratorImpl(PriFifoBucketMap::const_iterator pri_fifo_iter,
                                        PriFifoBucketMap::const_iterator pri_fifo_end)
            : _pri_fifo_iter(pri_fifo_iter),
              _pri_fifo_end(pri_fifo_end)
        {}
        ~PriFifoMappingConstIteratorImpl() override = default;

        void increment() override;
        bool equal(const ConstIteratorImpl& other) const override;
        PrioritizedBucket dereference() const override;
    };

    void clearAllEntriesForBucket(const document::Bucket &bucket);

    PriFifoBucketMap       _pri_fifo_buckets;
    BucketToPriIteratorMap _bucket_to_pri_iterators;
    uint64_t               _fifo_seq_num;
};

}
