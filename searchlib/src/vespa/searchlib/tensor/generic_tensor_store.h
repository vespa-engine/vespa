// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "tensor_store.h"

namespace search {

namespace attribute {

/**
 * Class for storing serialized tensors in memory, used by TensorAttribute.
 *
 * Serialization format is subject to change.  Changes to serialization format
 * might also require corresponding changes to implemented optimized tensor
 * operations that use the serialized tensor as argument.
 */
class GenericTensorStore : public TensorStore
{
public:
    GenericTensorStore();

    virtual ~GenericTensorStore();

    std::pair<const void *, uint32_t> getRawBuffer(RefType ref) const;

    std::pair<void *, RefType> allocRawBuffer(uint32_t size);

    virtual void holdTensor(RefType ref) override;

    virtual RefType move(RefType ref) override;

    std::unique_ptr<Tensor> getTensor(RefType ref) const;

    RefType setTensor(const Tensor &tensor);
};


}  // namespace search::attribute

}  // namespace search
