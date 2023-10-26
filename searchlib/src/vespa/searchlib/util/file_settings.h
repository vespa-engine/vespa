// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/util/size_literals.h>

namespace search {

/**
 * Common settings that is used for file I/O.
 */
struct FileSettings {
    // The alignment (in bytes) used for DIRECT I/O write and read.
    static constexpr size_t DIRECTIO_ALIGNMENT = 4_Ki;
};

}
