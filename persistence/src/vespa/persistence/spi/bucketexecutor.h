// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "bucket.h"
#include <memory>

namespace vespalib { class IDestructorCallback; }

namespace storage::spi {


/**
 * Task that will be run in thread from content layer.
 * It will hold a bucket lock while run. At token is provided
 * for optional async completion. It must not be destructed until
 * you no longer require the bucket lock.
 */
class BucketTask {
public:
    virtual ~BucketTask() = default;
    virtual void run(const Bucket & bucket, std::shared_ptr<vespalib::IDestructorCallback> onComplete) = 0;
    virtual void fail(const Bucket &) = 0;
};

/**
 * Interface for running a BucketTask. If running the task fails either synchronously or asynchronously.
 * The fail method will be invoked, either synchronously or asynchronously.
 */
struct BucketExecutor {
    virtual ~BucketExecutor() = default;
    virtual void execute(const Bucket & bucket, std::unique_ptr<BucketTask> task) = 0;
};

}
