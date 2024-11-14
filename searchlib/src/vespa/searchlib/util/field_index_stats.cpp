// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "field_index_stats.h"
#include <ostream>

namespace search {

std::ostream& operator<<(std::ostream& os, const FieldIndexStats& stats) {
    os << "{memory: " << stats.memory_usage() << ", disk: " << stats.size_on_disk() <<
    ", diskio: " << stats.cache_disk_io_stats() << "}";
    return os;
}

}
