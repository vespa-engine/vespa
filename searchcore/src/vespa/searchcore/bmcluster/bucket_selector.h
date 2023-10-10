// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

namespace search::bmcluster {

/*
 * Map from document index to bucket to ensure even spread between buckets
 * while ensuring that each bucket used belong to a specific thread.
 */
class BucketSelector
{
    uint32_t _thread_id;
    uint32_t _threads;
    uint32_t _num_buckets;
public:
    BucketSelector(uint32_t thread_id_in, uint32_t threads_in, uint32_t num_buckets_in)
        : _thread_id(thread_id_in),
          _threads(threads_in),
          _num_buckets((num_buckets_in / _threads) * _threads)
    {
    }
    uint64_t operator()(uint32_t i) const {
        return (static_cast<uint64_t>(i) * _threads + _thread_id) % _num_buckets;
    }
};

}
