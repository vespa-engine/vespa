// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "simplebucketprioritydatabase.h"
#include <vespa/vespalib/stllike/hash_map.hpp>
#include <cassert>
#include <ostream>
#include <sstream>

namespace storage::distributor {

SimpleBucketPriorityDatabase::SimpleBucketPriorityDatabase()
    : _pri_fifo_buckets(),
      _bucket_to_pri_iterators(),
      _fifo_seq_num(0)
{
}

SimpleBucketPriorityDatabase::~SimpleBucketPriorityDatabase() = default;

void
SimpleBucketPriorityDatabase::clearAllEntriesForBucket(const document::Bucket &bucket)
{
    auto maybe_iter = _bucket_to_pri_iterators.find(bucket);
    if (maybe_iter != _bucket_to_pri_iterators.end()) {
        _pri_fifo_buckets.erase(maybe_iter->second);
        _bucket_to_pri_iterators.erase(maybe_iter);
    }
}

void
SimpleBucketPriorityDatabase::setPriority(const PrioritizedBucket& bucket)
{
    clearAllEntriesForBucket(bucket.getBucket());
    if (bucket.requiresMaintenance()) {
        auto pri_insert_res = _pri_fifo_buckets.emplace(PriFifoCompositeKey(bucket.getPriority(), _fifo_seq_num),
                                                        bucket.getBucket());
        assert(pri_insert_res.second);
        ++_fifo_seq_num;
        auto inv_insert_res = _bucket_to_pri_iterators.insert(std::make_pair(bucket.getBucket(), pri_insert_res.first));
        assert(inv_insert_res.second);
    }
}

void
SimpleBucketPriorityDatabase::PriFifoMappingConstIteratorImpl::increment()
{
    if (_pri_fifo_iter != _pri_fifo_end) {
        ++_pri_fifo_iter;
    }
}

bool
SimpleBucketPriorityDatabase::PriFifoMappingConstIteratorImpl::equal(const ConstIteratorImpl& other) const
{
    auto& typed_other = dynamic_cast<const PriFifoMappingConstIteratorImpl&>(other);
    return (_pri_fifo_iter == typed_other._pri_fifo_iter);
}

PrioritizedBucket
SimpleBucketPriorityDatabase::PriFifoMappingConstIteratorImpl::dereference() const
{
    assert(_pri_fifo_iter != _pri_fifo_end);
    return {_pri_fifo_iter->second, _pri_fifo_iter->first._pri};
}

SimpleBucketPriorityDatabase::const_iterator
SimpleBucketPriorityDatabase::begin() const
{
    return const_iterator(std::make_unique<PriFifoMappingConstIteratorImpl>(
            _pri_fifo_buckets.begin(), _pri_fifo_buckets.end()));
}

SimpleBucketPriorityDatabase::const_iterator
SimpleBucketPriorityDatabase::end() const
{
    return const_iterator(std::make_unique<PriFifoMappingConstIteratorImpl>(
            _pri_fifo_buckets.end(), _pri_fifo_buckets.end()));
}

std::string
SimpleBucketPriorityDatabase::toString() const
{
    std::ostringstream ss;
    for (const auto& e : *this) {
        ss << e << '\n';
    }
    return ss.str();
}

}
