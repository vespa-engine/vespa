// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "disk_io_stats.h"
#include <ostream>

namespace search {

std::ostream& operator<<(std::ostream& os, const DiskIoStats& stats) {
    os << "{read_operations: " << stats.read_operations() << ", read_bytes: " << stats.read_bytes() << "}";
    return os;
}

}
