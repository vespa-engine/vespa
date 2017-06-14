// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "read_consistency.h"
#include <iostream>
#include <cassert>

namespace storage {
namespace spi {

std::ostream&
operator<<(std::ostream& os, ReadConsistency consistency)
{
    switch (consistency) {
    case ReadConsistency::STRONG:
        os << "STRONG";
        break;
    case ReadConsistency::WEAK:
        os << "WEAK";
        break;
    default:
        assert(false);
    }
    return os;
}

} // spi
} // storage

