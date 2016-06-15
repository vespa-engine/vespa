// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "output.h"
#include "type.h"
#include "buffered_input.h"
#include "buffered_output.h"

namespace vespalib {

class Slime;

namespace slime {

class Inspector;

struct JsonFormat {
    static void encode(const Inspector &inspector, Output &output, bool compact);
    static void encode(const Slime &slime, Output &output, bool compact);
    static size_t decode(const Memory &memory, Slime &slime);
};

} // namespace vespalib::slime
} // namespace vespalib

