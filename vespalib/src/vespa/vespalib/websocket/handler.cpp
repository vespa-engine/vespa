// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "handler.h"

namespace vespalib::ws {

namespace {

struct DummyItem {};

} // namespace vespalib::ws::<unnamed>

template struct Handler<DummyItem>;

} // namespace vespalib::ws
