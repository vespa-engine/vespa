// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "dummy_bucket_executor.h"
#include <vespa/vespalib/util/threadstackexecutor.h>
#include <vespa/vespalib/util/lambdatask.h>
#include <vespa/vespalib/util/destructor_callbacks.h>

using vespalib::makeLambdaTask;
using vespalib::makeLambdaCallback;

namespace storage::spi::dummy {

DummyBucketExecutor::DummyBucketExecutor(size_t numExecutors)
    : _executor(std::make_unique<vespalib::ThreadStackExecutor>(numExecutors, 0x10000)),
      _lock(),
      _cond(),
      _inFlight()
{
}

DummyBucketExecutor::~DummyBucketExecutor() {
    sync();
}

void
DummyBucketExecutor::execute(const Bucket & bucket, std::unique_ptr<BucketTask> task) {
    auto failed = _executor->execute(makeLambdaTask([this, bucket, bucketTask=std::move(task)]() {
        {
            std::unique_lock guard(_lock);
            // Use contains when dropping support for gcc 8.
            while (_inFlight.count(bucket.getBucket()) != 0) {
                _cond.wait(guard);
            }
            _inFlight.insert(bucket.getBucket());
        }
        bucketTask->run(bucket, makeLambdaCallback([this, bucket]() {
            std::unique_lock guard(_lock);
            // Use contains when dropping support for gcc 8.
            assert(_inFlight.count(bucket.getBucket()) != 0);
            _inFlight.erase(bucket.getBucket());
            _cond.notify_all();
        }));
    }));
    if (failed) {
        failed->run();
    }
}

void
DummyBucketExecutor::sync() {
    _executor->sync();
}

}
