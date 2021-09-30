// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/transactionlog/common.h>

namespace proton {

class FeedOperation;

/**
 * Interface for a component assigning serial numbers and storing feed operations.
 */
struct IOperationStorer
{
    using DoneCallback = search::transactionlog::Writer::DoneCallback;
    using CommitResult = search::transactionlog::Writer::CommitResult;
    virtual ~IOperationStorer() = default;

    /**
     * Assign serial number to (if not set) and store the given operation.
     */
    virtual void appendOperation(const FeedOperation &op, DoneCallback onDone) = 0;
    [[nodiscard]] virtual CommitResult startCommit(DoneCallback onDone) = 0;
    [[nodiscard]] CommitResult appendAndCommitOperation(const FeedOperation &op, DoneCallback onDone) {
        appendOperation(op, DoneCallback());
        return startCommit(std::move(onDone));
    }
};

} // namespace proton

