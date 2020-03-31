// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "parser_limits.h"
#include "parsing_failed_exception.h"

namespace document::select {

void throw_max_depth_exceeded_exception() {
    throw ParsingFailedException("expression is too deeply nested");
}

}
