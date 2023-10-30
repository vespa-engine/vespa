// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <memory>

namespace vespalib { class IDestructorCallback; }

namespace proton {

class MoveOperation;

/**
 * Interface used by DocumentBucketMover to handle the moving of a single document.
 */
struct IDocumentMoveHandler
{
    enum class MoveResult { SUCCESS, FAILURE};
    [[nodiscard]] virtual MoveResult handleMove(MoveOperation &op, std::shared_ptr<vespalib::IDestructorCallback> moveDoneCtx) = 0;
    virtual ~IDocumentMoveHandler() = default;
};

} // namespace proton

