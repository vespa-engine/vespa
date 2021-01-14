// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "bucket.h"
#include "operationcomplete.h"

namespace storage::spi {

/**
 * Task that will be run in thread from conetnt layer.
 * It will hold a bucket lock while run. At token is provided
 * for optional async completition. It must not be destructed until
 * you no longer require the bucket lock.
 */
class BucketTask {
public:
    virtual ~BucketTask() = default;
    virtual void run(OperationComplete::UP onComplete) = 0;
    const Bucket & getBucket() const { return _bucket; }
protected:
    BucketTask(const Bucket & bucket) : _bucket(bucket) { }
private:
    Bucket _bucket;
};

/**
 * Interface for running a BucketTask
 */
struct BucketExecutor {
    virtual ~BucketExecutor() = default;
    virtual void execute(std::unique_ptr<BucketTask> task) = 0;
};

}
