// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstdint>

namespace search::queryeval {

struct IDiversifier {
    virtual ~IDiversifier() {}
    /**
     * Will tell if this document should be kept, and update state for further filtering.
     */
    virtual bool accepted(uint32_t docId) = 0;
};
}
