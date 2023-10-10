// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "i_reprocessing_handler.h"

namespace proton {

/**
 * Interface for an initializer of a reprocessing handler.
 */
struct IReprocessingInitializer
{
    using UP = std::unique_ptr<IReprocessingInitializer>;

    virtual ~IReprocessingInitializer() {}

    /**
     * Returns whether this initializer has any reprocessors to add to the handler.
     */
    virtual bool hasReprocessors() const = 0;

    /**
     * Initialize the given handler by adding processing readers and/or rewriters.
     */
    virtual void initialize(IReprocessingHandler &handler) = 0;
};

} // namespace proton

