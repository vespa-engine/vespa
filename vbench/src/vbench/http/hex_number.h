// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.


#pragma once

#include <cstddef>

namespace vbench {

/**
 * Utility class used to work with hex number used in HTTP chunks.
 **/
class HexNumber
{
private:
    size_t _value;
    size_t _length;

public:
    HexNumber(const char *str);
    size_t value() const { return _value; }
    size_t length() const { return _length; }
};

} // namespace vbench
