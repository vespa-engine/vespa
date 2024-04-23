// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "int8float.h"
#include <vespa/vespalib/objects/nbostream.h>


namespace vespalib::eval {

nbostream &operator<<(nbostream &stream, Int8Float v) {
    return stream << v.get_bits();
}

nbostream &operator>>(nbostream &stream, Int8Float &v) {
    int8_t byte;
    stream >> byte;
    v.assign_bits(byte);
    return stream;
}

}
