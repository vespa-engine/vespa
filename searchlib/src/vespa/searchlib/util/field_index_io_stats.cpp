// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "field_index_io_stats.h"
#include <ostream>

namespace search {

std::ostream& operator<<(std::ostream& os, const FieldIndexIoStats& stats) {
    os << "{read: " << stats.read() << ", cached_read: " << stats.cached_read() << "}";
    return os;
}

}
