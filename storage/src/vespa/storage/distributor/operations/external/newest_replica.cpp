// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "newest_replica.h"
#include <ostream>

namespace storage::distributor {

std::ostream& operator<<(std::ostream& os, const NewestReplica& nr) {
    os << "NewestReplica(timestamp " << nr.timestamp
       << ", bucket_id " << nr.bucket_id
       << ", node " << nr.node
       << ", is_tombstone " << nr.is_tombstone
       << ", condition_matched " << nr.condition_matched
       << ')';
    return os;
}

}
