// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "bucketdb.h"
#include <mutex>

namespace proton {

/**
 * Class that owns and provides guarded access to a bucket database.
 */
class BucketDBOwner
{
    using Mutex = std::mutex;

public:
    class Guard
    {
    private:
        BucketDB *_bucketDB;
        std::unique_lock<Mutex> _guard;

    public:
        Guard(BucketDB *bucketDB, Mutex &mutex);
        Guard(const Guard &) = delete;
        Guard(Guard &&rhs);
        Guard &operator=(const Guard &) = delete;
        Guard &operator=(Guard &&rhs) = delete;
        BucketDB *operator->() { return _bucketDB; }
        BucketDB &operator*() { return *_bucketDB; }
        const BucketDB *operator->() const { return _bucketDB; }
        const BucketDB &operator*() const { return *_bucketDB; }
    };

private:
    BucketDB _bucketDB;
    Mutex _mutex;

public:
    typedef std::shared_ptr<BucketDBOwner> SP;

    BucketDBOwner();
    Guard takeGuard() {
        return Guard(&_bucketDB, _mutex);
    }
    const BucketDB & getBucketDB() const { return _bucketDB; }
};

} // namespace proton
