// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "bucketscaniterator.h"

using document::BucketId;
using storage::spi::BucketInfo;

namespace proton::bucketdb {

ScanIterator::ScanIterator(const Guard & db, Pass pass, BucketId lastBucket, BucketId endBucket)
    : _db(std::move(db)),
      _itr(lastBucket.isSet() ? _db->upperBound(lastBucket) : _db->begin()),
      _end(pass == Pass::SECOND && endBucket.isSet() ?
           _db->upperBound(endBucket) : _db->end())
{ }

ScanIterator::ScanIterator(const Guard & db, BucketId bucket)
    : _db(std::move(db)),
      _itr(_db->lowerBound(bucket)),
      _end(_db->end())
{ }

} // namespace proton
