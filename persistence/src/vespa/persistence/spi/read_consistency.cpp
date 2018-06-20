// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "read_consistency.h"
#include <iostream>

#include <vespa/log/log.h>
LOG_SETUP(".persistence.spi.read_consistency");

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
        LOG_ABORT("should not reach here");
    }
    return os;
}

} // spi
} // storage

