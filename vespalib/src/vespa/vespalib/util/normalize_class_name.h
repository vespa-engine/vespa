// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/stllike/string.h>

namespace vespalib {

/*
 * Normalize a demangled class name to compensate for different demangling
 * with g++ / libstdc++ / binutils and clang++ / libc++ / llvm toolchains.
 */
vespalib::string normalize_class_name(vespalib::string class_name);

}
