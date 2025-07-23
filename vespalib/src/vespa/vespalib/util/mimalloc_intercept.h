// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

namespace vespalib {

// Implementation of the `mimalloc_register_error` function callback which
// prints a stack trace and quick-exits the process on OOM, or invokes abort()
// upon all other reported failure conditions.
__attribute__((noreturn))
void terminate_on_mi_malloc_failure(int err, void* fwd_arg);

}
