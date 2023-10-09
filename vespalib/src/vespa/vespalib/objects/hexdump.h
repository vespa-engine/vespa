// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/stllike/string.h>

namespace vespalib {

class asciistream;

/*
 * Helper class to provide hex dump of the contents in a buffer.
 */
class HexDump
{
public:
    HexDump(const void * buf, size_t sz) : _buf(buf), _sz(sz) { }
    vespalib::string toString() const;
    friend std::ostream & operator << (std::ostream & os, const HexDump & hd);
    friend asciistream & operator << (asciistream & os, const HexDump & hd);
private:
    const void * _buf;
    size_t       _sz;
};

}
