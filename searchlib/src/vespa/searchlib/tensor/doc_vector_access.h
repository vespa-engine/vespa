// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/eval/eval/typed_cells.h>
#include <cstdint>

namespace search::tensor {

class VectorBundle;

/**
 * Interface that provides access to the vector that is associated with the the given document id.
 *
 * All vectors should be the same size and either of type float or double.
 */
class DocVectorAccess {
public:
    virtual ~DocVectorAccess() = default;
    virtual vespalib::eval::TypedCells get_vector(uint32_t docid, uint32_t subspace) const noexcept = 0;
    virtual VectorBundle get_vectors(uint32_t docid) const noexcept = 0;

    /**
     * Try to prefetch into memory data needed to resolve docid into corresponding tensor.
     *
     * In some implementations in order to resolve docid into corresponding tensor we have to go
     * through a level of indirection, which might cause memory-cache misses on its own.
     * In such implementations one could implement this method to prefetch this indirection first,
     * and the calling code would try its best to give this prefetch enough time to bring the
     * data needed in before invoking `prefetch_vector`.
     */
    virtual void prefetch_docid(uint32_t docid) const noexcept {
        (void)docid;
    }

    /**
     * Try to prefetch tensor's data into memory.
     */
    virtual void prefetch_vector(uint32_t docid) const noexcept {
        (void)docid;
    }
};

}
