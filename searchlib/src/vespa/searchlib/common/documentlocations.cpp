// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/fastos/fastos.h>
#include "documentlocations.h"

namespace search {
namespace common {

DocumentLocations::DocumentLocations(void)
    : _vec_guard(new AttributeGuard),
      _vec(NULL) {
}

}  // namespace common
}  // namespace search
