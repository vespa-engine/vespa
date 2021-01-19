// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "bucketexecutor.h"

namespace storage::spi {

template<class FunctionType>
class LambdaBucketTask : public BucketTask {
public:
    explicit LambdaBucketTask(FunctionType &&func)
            : _func(std::move(func))
    {}

    ~LambdaBucketTask() override = default;

    void run(const Bucket & bucket, std::shared_ptr<vespalib::IDestructorCallback> onComplete) override {
        _func(bucket, std::move(onComplete));
    }

private:
    FunctionType _func;
};

template<class FunctionType>
std::unique_ptr<BucketTask>
makeBucketTask(FunctionType &&function) {
    return std::make_unique<LambdaBucketTask<std::decay_t<FunctionType>>>
    (std::forward<FunctionType>(function));
}
    
}
