// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "predicate_interval.h"
#include <ostream>

namespace search::predicate {

std::ostream &
operator<<(std::ostream &out, const Interval &i) {
    std::ios_base::fmtflags flags = out.flags();
    out << "0x" << std::hex << i.interval;
    out.flags(flags);
    return out;
}

std::ostream &
operator<<(std::ostream &out, const IntervalWithBounds &i) {
    std::ios_base::fmtflags flags = out.flags();
    out << "0x" << std::hex << i.interval << ", 0x" << i.bounds;
    out.flags(flags);
    return out;
}

}
