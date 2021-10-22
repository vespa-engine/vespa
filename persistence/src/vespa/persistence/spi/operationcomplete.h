// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <memory>

namespace storage::spi {

class Result;

class ResultHandler {
public:
    virtual ~ResultHandler() = default;
    virtual void handle(const Result &) const = 0;
};

/**
 * This is the callback interface when using the async operations
 * in the persistence provider.
 */
class OperationComplete
{
public:
    using UP = std::unique_ptr<OperationComplete>;
    virtual ~OperationComplete() = default;
    virtual void onComplete(std::unique_ptr<Result> result) noexcept = 0;
    virtual void addResultHandler(const ResultHandler * resultHandler) = 0;
};

}
