// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.#pragma once

#pragma once

#include <memory>

namespace storage::spi {

class Result;

/**
 * This is the callback interface when using the async operations
 * in the persistence provider.
 */
class OperationComplete
{
public:
    using UP = std::unique_ptr<OperationComplete>;
    virtual ~OperationComplete() = default;
    virtual void onComplete(std::unique_ptr<Result> result) = 0;
};

}