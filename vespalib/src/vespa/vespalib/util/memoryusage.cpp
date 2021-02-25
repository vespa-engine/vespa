// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "memoryusage.h"
#include <vespa/vespalib/stllike/asciistream.h>

namespace vespalib {

string
MemoryUsage::toString() const {
    vespalib::asciistream os;
    os << *this;
    return os.str();
}

asciistream &
operator << (asciistream & os, const MemoryUsage & usage) {
    os << "allocated: " << usage.allocatedBytes();
    os << ", used: " << usage.usedBytes();
    os << ", dead: " << usage.deadBytes();
    os << ", onhold: " << usage.allocatedBytesOnHold();
    return os;
}

} // namespace vespalib
