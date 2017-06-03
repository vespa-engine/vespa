// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "types.h"
#include <vespa/vespalib/util/exceptions.h>
#include <cassert>
#include <sstream>

namespace storage::memfile {

const framework::MicroSecTime Types::MAX_TIMESTAMP(framework::MicroSecTime::max());
const framework::MicroSecTime Types::UNSET_TIMESTAMP(0);

void
Types::verifyLegalFlags(uint32_t flags, uint32_t legal, const char* operation)
{
    if ((flags & legal) != flags) {
        std::ostringstream ost;
        ost << "Invalid flags given to operation " << operation << ". "
            << std::hex << flags << " given, but only " << legal
            << " are legal.";
        throw vespalib::IllegalArgumentException(ost.str(), VESPA_STRLOC);
    }
}

std::ostream&
operator<<(std::ostream& os, const DataLocation& loc)
{
    os << "DataLocation("
       << std::dec
       << loc._pos
       << ", "
       << loc._size
       << ")";
    return os;
}

const char*
Types::getMemFileFlagName(MemFileFlag flag) {
    switch (flag) {
        case FILE_EXIST: return "FILE_EXIST";
        case HEADER_BLOCK_READ: return "HEADER_BLOCK_READ";
        case BODY_BLOCK_READ: return "BODY_BLOCK_READ";
        case BUCKET_INFO_OUTDATED: return "BUCKET_INFO_OUTDATED";
        case SLOTS_ALTERED: return "SLOTS_ALTERED";
        case LEGAL_MEMFILE_FLAGS: assert(false); // Not a single flag
        default: return "INVALID";
    }
}

}
