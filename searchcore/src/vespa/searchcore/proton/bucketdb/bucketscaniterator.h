// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "bucket_db_owner.h"
#include "bucketdb.h"

namespace proton::bucketdb {

struct ScanPosition {
    document::BucketId _lastBucket;

    ScanPosition() : _lastBucket() { }
    bool validBucket() const { return _lastBucket.isSet(); }
};


class ScanIterator {
private:
    using BucketId = document::BucketId;
    using BucketIterator = BucketDB::ConstMapIterator;
    const Guard         &_db;
    BucketIterator       _itr;
    BucketIterator       _end;

public:
    enum class Pass {FIRST, SECOND};
    ScanIterator(const Guard & db, Pass pass, BucketId lastBucket, BucketId endBucket);
    ScanIterator(const Guard & db, BucketId bucket);
    ScanIterator(const ScanIterator &) = delete;
    ScanIterator(ScanIterator &&rhs) = delete;
    ScanIterator &operator=(const ScanIterator &) = delete;
    ScanIterator &operator=(ScanIterator &&rhs) = delete;

    bool                   valid() const { return _itr != _end; }
    bool                isActive() const { return _itr->second.isActive(); }
    BucketId           getBucket() const { return _itr->first; }
    bool      hasReadyBucketDocs() const { return _itr->second.getReadyCount() != 0; }
    bool   hasNotReadyBucketDocs() const { return _itr->second.getNotReadyCount() != 0; }

    ScanIterator & operator++() {
        ++_itr;
        return *this;
    }
};

}
