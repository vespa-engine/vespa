// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "handler.h"

namespace vbench {

namespace {

struct DummyItem {};

} // namespace vbench::<unnamed>

template struct Handler<DummyItem>;

} // namespace vbench
