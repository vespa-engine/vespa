// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "basic_value.h"
#include <vespa/vespalib/util/stash.h>

namespace vespalib::slime {

namespace {

Memory
store(Memory m, Stash & stash)
{
    char * buf = stash.alloc(m.size);
    if (m.data) {
        memcpy(buf, m.data, m.size);
    }
    return Memory(buf, m.size);
}

}

BasicStringValue::BasicStringValue(Memory m, Stash & stash)
    : _value(store(m, stash))
{
}

BasicDataValue::BasicDataValue(Memory m, Stash & stash)
    : _value(store(m, stash))
{
}

} // namespace vespalib::slime
