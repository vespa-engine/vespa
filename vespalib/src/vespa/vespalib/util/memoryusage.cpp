// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "memoryusage.h"
#include <vespa/vespalib/stllike/asciistream.h>
#include <ostream>

namespace vespalib {

std::string
MemoryUsage::toString() const {
    vespalib::asciistream os;
    os << *this;
    return os.str();
}

asciistream &
operator << (asciistream & os, const MemoryUsage & usage) {
    os << "{allocated: " << usage.allocatedBytes();
    os << ", used: " << usage.usedBytes();
    os << ", dead: " << usage.deadBytes();
    os << ", onhold: " << usage.allocatedBytesOnHold() << "}";
    return os;
}

std::ostream& operator<<(std::ostream& os, const MemoryUsage& usage) {
    os << usage.toString();
    return os;
}

} // namespace vespalib
