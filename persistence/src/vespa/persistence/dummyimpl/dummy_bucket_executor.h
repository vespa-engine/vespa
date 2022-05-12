// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/persistence/spi/bucketexecutor.h>
#include <vespa/vespalib/util/threadexecutor.h>
#include <deque>
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
    void defer_new_tasks();
    [[nodiscard]] size_t num_deferred_tasks() const noexcept;
    void schedule_single_deferred_task();
    void schedule_all_deferred_tasks();
private:
    void internal_execute_no_defer(const Bucket & bucket, std::unique_ptr<BucketTask> task);

    using DeferredTasks = std::deque<std::pair<Bucket, std::unique_ptr<BucketTask>>>;

    std::unique_ptr<vespalib::SyncableThreadExecutor> _executor;
    mutable std::mutex                                _lock;
    std::condition_variable                           _cond;
    std::unordered_set<document::Bucket, document::Bucket::hash> _inFlight;
    bool          _defer_tasks;
    DeferredTasks _deferred_tasks;
};

}
