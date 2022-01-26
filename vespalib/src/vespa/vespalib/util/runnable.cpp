// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "runnable.h"

namespace vespalib {

int
Runnable::default_init_function(Runnable &target)
{
    target.run();
    return 1;
}

} // namespace vespalib
