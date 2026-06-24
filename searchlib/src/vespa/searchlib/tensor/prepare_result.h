// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

namespace search::tensor {

/**
 * Interface for a class used to keep the result of the prepare step of a two-phase operation.
 */
class PrepareResult {
public:
    virtual ~PrepareResult() = default;
    // True if this is the result of preparing an in-place partial (per-subspace) update of a multi-vector
    // document. Such a result is applied via complete_partial_update_remove()/complete_partial_update_add()
    // around the tensor-store swap, instead of complete_add_document().
    virtual bool is_partial_update() const noexcept { return false; }
};

} // namespace search::tensor
