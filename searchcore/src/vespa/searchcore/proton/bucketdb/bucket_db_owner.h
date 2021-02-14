// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "bucketdb.h"
#include <mutex>

namespace proton::bucketdb {

class Guard
{
public:
    Guard(BucketDB *bucketDB, std::mutex &mutex);
    Guard(const Guard &) = delete;
    Guard(Guard &&rhs);
    Guard &operator=(const Guard &) = delete;
    Guard &operator=(Guard &&rhs) = delete;
    BucketDB *operator->() { return _bucketDB; }
    BucketDB &operator*() { return *_bucketDB; }
    const BucketDB *operator->() const { return _bucketDB; }
    const BucketDB &operator*() const { return *_bucketDB; }
private:
    BucketDB *_bucketDB;
    std::unique_lock<std::mutex> _guard;
};

/**
 * Class that owns and provides guarded access to a bucket database.
 */
class BucketDBOwner
{
public:
    BucketDBOwner();
    Guard takeGuard() {
        return Guard(&_bucketDB, _mutex);
    }
private:
    BucketDB   _bucketDB;
    std::mutex _mutex;
};

}
