// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "type.h"
#include <vespa/vespalib/data/output.h>
#include <vespa/vespalib/data/input_reader.h>
#include <vespa/vespalib/data/output_writer.h>

namespace vespalib { class Slime; }

namespace vespalib::slime {

struct Inspector;

struct JsonFormat {
    static void encode(const Inspector &inspector, Output &output, bool compact);
    static void encode(const Slime &slime, Output &output, bool compact);
    static size_t decode(Input &input, Slime &slime);
    static size_t decode(const Memory &memory, Slime &slime);
};

} // namespace vespalib::slime
