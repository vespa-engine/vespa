// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <memory>

namespace config {

/*
 * Class representing a source from which constructs config requests and request
 * handlers.
 */
class Source {
public:
    virtual void getConfig() = 0;
    virtual void reload(int64_t generation) = 0;
    virtual void close() = 0;

    virtual ~Source() = default;
};

} // namespace common

