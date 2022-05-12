// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "dummy_bucket_executor.h"
#include <vespa/vespalib/util/threadstackexecutor.h>
#include <vespa/vespalib/util/lambdatask.h>
#include <vespa/vespalib/util/destructor_callbacks.h>

using vespalib::makeLambdaTask;
using vespalib::makeSharedLambdaCallback;

namespace storage::spi::dummy {

DummyBucketExecutor::DummyBucketExecutor(size_t numExecutors)
    : _executor(std::make_unique<vespalib::ThreadStackExecutor>(numExecutors, 0x10000)),
      _lock(),
      _cond(),
      _inFlight(),
      _defer_tasks(false),
      _deferred_tasks()
{
}

DummyBucketExecutor::~DummyBucketExecutor() {
    sync();
}

void
DummyBucketExecutor::execute(const Bucket & bucket, std::unique_ptr<BucketTask> task) {
    if (!_defer_tasks) {
        internal_execute_no_defer(bucket, std::move(task));
    } else {
        _deferred_tasks.emplace_back(bucket, std::move(task));
    }
}

void
DummyBucketExecutor::internal_execute_no_defer(const Bucket& bucket, std::unique_ptr<BucketTask> task) {
    auto failed = _executor->execute(makeLambdaTask([this, bucket, bucketTask=std::move(task)]() {
        {
            std::unique_lock guard(_lock);
            while (_inFlight.contains(bucket.getBucket())) {
                _cond.wait(guard);
            }
            _inFlight.insert(bucket.getBucket());
        }
        bucketTask->run(bucket, makeSharedLambdaCallback([this, bucket]() {
            std::unique_lock guard(_lock);
            assert(_inFlight.contains(bucket.getBucket()));
            _inFlight.erase(bucket.getBucket());
            _cond.notify_all();
        }));
    }));
    if (failed) {
        failed->run();
    }
}

void
DummyBucketExecutor::defer_new_tasks() {
    std::lock_guard guard(_lock);
    _defer_tasks = true;
}

void
DummyBucketExecutor::schedule_all_deferred_tasks() {
    DeferredTasks to_run;
    {
        std::lock_guard guard(_lock);
        assert(_defer_tasks);
        _deferred_tasks.swap(to_run);
    }
    for (auto& bucket_and_task : to_run) {
        internal_execute_no_defer(bucket_and_task.first, std::move(bucket_and_task.second));
    }
}

size_t
DummyBucketExecutor::num_deferred_tasks() const noexcept {
    std::lock_guard guard(_lock);
    return _deferred_tasks.size();
}

void
DummyBucketExecutor::schedule_single_deferred_task() {
    std::pair<Bucket, std::unique_ptr<BucketTask>> bucket_and_task;
    {
        std::lock_guard guard(_lock);
        assert(_defer_tasks);
        assert(!_deferred_tasks.empty());
        bucket_and_task = std::move(_deferred_tasks.front());
        _deferred_tasks.pop_front();
    }
    internal_execute_no_defer(bucket_and_task.first, std::move(bucket_and_task.second));
}

void
DummyBucketExecutor::sync() {
    _executor->sync();
}

}
