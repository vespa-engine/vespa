// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

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
    virtual ~DocVectorAccess() {}
    virtual vespalib::eval::TypedCells get_vector(uint32_t docid, uint32_t subspace) const = 0;
    virtual VectorBundle get_vectors(uint32_t docid) const = 0;
};

}
