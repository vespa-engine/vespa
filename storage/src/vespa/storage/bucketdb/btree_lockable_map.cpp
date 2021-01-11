// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "btree_lockable_map.hpp"
#include <vespa/vespalib/datastore/buffer_type.hpp>

namespace storage::bucketdb {

template class BTreeLockableMap<StorageBucketInfo>; // Forced instantiation.

}

namespace vespalib::datastore {

template class BufferType<storage::bucketdb::StorageBucketInfo>;

}
