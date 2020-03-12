#include "newest_replica.h"
#include <ostream>

namespace storage::distributor {

std::ostream& operator<<(std::ostream& os, const NewestReplica& nr) {
    os << "NewestReplica(timestamp " << nr.timestamp
       << ", bucket_id " << nr.bucket_id
       << ", node " << nr.node << ')';
    return os;
}

}
