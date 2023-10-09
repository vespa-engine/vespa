// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "hex_number.h"

namespace vbench {

HexNumber::HexNumber(const char *str)
    : _value(0),
      _length(0)
{
    while (*str != '\0') {
        switch (*str++) {
        case '0': _value = (_value << 4) + 0; break;
        case '1': _value = (_value << 4) + 1; break;
        case '2': _value = (_value << 4) + 2; break;
        case '3': _value = (_value << 4) + 3; break;
        case '4': _value = (_value << 4) + 4; break;
        case '5': _value = (_value << 4) + 5; break;
        case '6': _value = (_value << 4) + 6; break;
        case '7': _value = (_value << 4) + 7; break;
        case '8': _value = (_value << 4) + 8; break;
        case '9': _value = (_value << 4) + 9; break;
        case 'a': case 'A': _value = (_value << 4) + 10; break;
        case 'b': case 'B': _value = (_value << 4) + 11; break;
        case 'c': case 'C': _value = (_value << 4) + 12; break;
        case 'd': case 'D': _value = (_value << 4) + 13; break;
        case 'e': case 'E': _value = (_value << 4) + 14; break;
        case 'f': case 'F': _value = (_value << 4) + 15; break;
        default: return;
        }
        ++_length;
    }
}

} // namespace vbench
