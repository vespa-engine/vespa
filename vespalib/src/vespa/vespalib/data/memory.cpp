// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "memory.h"
#include <vespa/vespalib/util/stringfmt.h>

namespace vespalib {

vespalib::string
Memory::make_string() const
{
    return vespalib::string(data, size);
}

std::ostream &
operator<<(std::ostream &os, const Memory &memory) {
    uint32_t written = 0;
    uint32_t hexCount = 25;
    os << "size: " << memory.size << "(bytes)" << std::endl;
    for (size_t i = 0; i < memory.size; ++i, ++written) {
        if (written > hexCount) {
            os << std::endl;
            written = 0;
        }
        os << make_string("0x%02x ", memory.data[i] & 0xff);
    }
    if (written > 0) {
        os << std::endl;
    }
    return os;
}

} // namespace vespalib
