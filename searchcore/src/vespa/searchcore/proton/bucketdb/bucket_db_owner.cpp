// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "bucket_db_owner.h"

namespace proton {

BucketDBOwner::Guard::Guard(BucketDB *bucketDB, Mutex &mutex)
    : _bucketDB(bucketDB),
      _guard(mutex)
{
}


BucketDBOwner::Guard::Guard(Guard &&rhs)
    : _bucketDB(rhs._bucketDB),
      _guard(std::move(rhs._guard))
{
}


BucketDBOwner::BucketDBOwner()
    : _bucketDB(),
      _mutex()
{
}

} // namespace proton
