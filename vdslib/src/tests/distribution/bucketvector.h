// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vector>
#include <vespa/document/bucket/bucketid.h>

namespace BucketVector{

    void reserve(size_t capacity);
    void clear();
    void addBucket(uint64_t bucket);
    void getBuckets(uint32_t distributionBits, std::vector<document::BucketId>& buckets);
    void printVector();
}
