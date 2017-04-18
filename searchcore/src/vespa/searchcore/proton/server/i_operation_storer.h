// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

namespace proton {

class FeedOperation;

/**
 * Interface for a component assigning serial numbers and storing feed operations.
 */
struct IOperationStorer
{
    virtual ~IOperationStorer() {}

    /**
     * Assign serial number to (if not set) and store the given operation.
     */
    virtual void storeOperation(FeedOperation &op) = 0;
};

} // namespace proton

