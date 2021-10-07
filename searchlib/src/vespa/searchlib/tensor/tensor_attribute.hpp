// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

namespace search {

namespace tensor {

template <typename RefType>
void
TensorAttribute::doCompactWorst()
{
    uint32_t bufferId = _tensorStore.startCompactWorstBuffer();
    size_t lidLimit = _refVector.size();
    for (uint32_t lid = 0; lid < lidLimit; ++lid) {
        RefType ref = _refVector[lid];
        (void) ref;
        if (ref.valid() && ref.bufferId() == bufferId) {
            RefType newRef = _tensorStore.move(ref);
            // TODO: validate if following fence is sufficient.
            std::atomic_thread_fence(std::memory_order_release);
            _refVector[lid] = newRef;
        }
    }
    _tensorStore.finishCompactWorstBuffer(bufferId);
    _compactGeneration = getCurrentGeneration();
    incGeneration();
    updateStat(true);
}

}  // namespace search::tensor

}  // namespace search
