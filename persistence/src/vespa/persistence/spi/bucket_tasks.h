// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "bucketexecutor.h"

namespace storage::spi {

/**
 * Simple Bucket task that wraps a lambda that does the job.
 */
template<class FunctionType, class FailedFunction>
class LambdaBucketTask : public BucketTask {
public:
    explicit LambdaBucketTask(FunctionType &&func, FailedFunction &&failed)
        : _func(std::move(func)),
          _failed(std::move(failed))
    {}

    ~LambdaBucketTask() override = default;

    void run(const Bucket & bucket, std::shared_ptr<vespalib::IDestructorCallback> onComplete) override {
        _func(bucket, std::move(onComplete));
    }
    void fail(const Bucket & bucket) override {
        _failed(bucket);
    }

private:
    FunctionType   _func;
    FailedFunction _failed;
};

template<class FunctionType, class FailedFunction>
std::unique_ptr<BucketTask>
makeBucketTask(FunctionType &&function, FailedFunction && failed) {
    return std::make_unique<LambdaBucketTask<std::decay_t<FunctionType>, std::decay_t<FailedFunction>>>
    (std::forward<FunctionType>(function), std::forward<FailedFunction>(failed));
}
    
}
