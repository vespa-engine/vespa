// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "cell_type.h"
#include <stdio.h>
#include <cstdlib>
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/vespalib/util/stringfmt.h>

using vespalib::make_string_short::fmt;

namespace vespalib::eval {

void
CellTypeUtils::bad_argument(uint32_t id)
{
    throw IllegalArgumentException(fmt("Unknown CellType id=%u", id));
}

}
