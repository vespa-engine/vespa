// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.


#pragma once

#include <memory>
#include <vespa/vespalib/stllike/string.h>
#include "buffer.h"

namespace vespalib {
namespace ws {

struct Frame {
    enum class Type { NONE, TEXT, DATA, PING, PONG, CLOSE, INVALID };
    Type type;
    bool last;
    Buffer payload;
    Frame() : type(Type::INVALID), last(true), payload() {}
};

} // namespace vespalib::ws
} // namespace vespalib
