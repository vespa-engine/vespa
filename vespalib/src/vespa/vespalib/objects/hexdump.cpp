// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "hexdump.h"
#include <vespa/vespalib/stllike/asciistream.h>

namespace vespalib {

namespace {

    const char * hexChar = "0123456789ABCDEF";

}

string
HexDump::toString() const {
    asciistream os;
    os << *this;
    return os.str();
}

asciistream & operator << (asciistream & os, const HexDump & hd)
{
    os << hd._sz << ' ';
    const uint8_t *c = static_cast<const uint8_t *>(hd._buf);
    for (size_t i(0); i < hd._sz; i++) {
        os << hexChar[c[i] >> 4] << hexChar[c[i] & 0xf];
    }
    return os;
}

std::ostream & operator << (std::ostream & os, const HexDump & hd)
{
    return os << hd.toString();
}

}
