// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "lockablemap.hpp"
#include "storagebucketinfo.h"
#include "judymultimap.h"

namespace storage {

using bucketdb::StorageBucketInfo;

template class LockableMap<storage::JudyMultiMap<StorageBucketInfo, StorageBucketInfo, StorageBucketInfo, StorageBucketInfo> >;

}
