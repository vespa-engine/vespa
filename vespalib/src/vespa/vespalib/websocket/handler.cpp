// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.


#include <vespa/fastos/fastos.h>
#include "handler.h"

namespace vespalib {
namespace ws {

namespace {

struct DummyItem {};

} // namespace vespalib::ws::<unnamed>

template class Handler<DummyItem>;

} // namespace vespalib::ws
} // namespace vespalib
