// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "bucket.h"

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
};

/**
 * Interface for running a BucketTask. If it fails the task will be returned.
 * That would normally indicate a fatal error.
 * sync() will be called during detatching to ensure the implementation is drained.
 */
struct BucketExecutor {
    virtual ~BucketExecutor() = default;
    virtual std::unique_ptr<BucketTask> execute(const Bucket & bucket, std::unique_ptr<BucketTask> task) = 0;
    virtual void sync() = 0;
};

}
