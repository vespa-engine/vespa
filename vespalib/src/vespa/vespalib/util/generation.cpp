// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "generation.h"

#include <ostream>

namespace vespalib {

std::ostream& operator<<(std::ostream& os, const Generation& generation) {
    os << generation.value();
    return os;
}

} // namespace vespalib
