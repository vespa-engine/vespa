// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/persistence/spi/bucketexecutor.h>
#include <vespa/vespalib/util/threadexecutor.h>
#include <mutex>
#include <condition_variable>
#include <unordered_set>

namespace storage::spi::dummy {

/**
 * Simple implementation of a bucket executor. It can schedule multiple tasks concurrently, but only one per bucket.
 */
class DummyBucketExecutor : public BucketExecutor {
public:
    DummyBucketExecutor(size_t numExecutors);
    ~DummyBucketExecutor() override;
    void execute(const Bucket & bucket, std::unique_ptr<BucketTask> task) override;
    void sync();
private:
    std::unique_ptr<vespalib::SyncableThreadExecutor> _executor;
    std::mutex                                        _lock;
    std::condition_variable                           _cond;
    std::unordered_set<document::Bucket, document::Bucket::hash>    _inFlight;
};

}
