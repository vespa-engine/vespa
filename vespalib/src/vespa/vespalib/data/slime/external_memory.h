// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <memory>
#include <vespa/vespalib/data/memory.h>

namespace vespalib::slime {

/**
 * Interface used to access external memory. External memory does not
 * need to be copied when added to a Slime object. The Memory obtained
 * by calling the get function must be valid until the object
 * implementing this interface is destructed.
 **/
struct ExternalMemory {
    using UP = std::unique_ptr<ExternalMemory>;
    virtual Memory get() const = 0;
    virtual ~ExternalMemory() = default;
};

} // namespace vespalib::slime
