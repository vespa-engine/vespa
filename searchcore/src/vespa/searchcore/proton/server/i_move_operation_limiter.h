// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <memory>

namespace vespalib { class IDestructorCallback; }

namespace proton {

/**
 * Interface used to limit the number of outstanding move operations a blockable maintenance job can have.
 */
struct IMoveOperationLimiter {
    virtual ~IMoveOperationLimiter() = default;
    virtual std::shared_ptr<vespalib::IDestructorCallback> beginOperation() = 0;
    virtual size_t numPending() const = 0;
};

}
