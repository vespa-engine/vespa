// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "tensor_address.h"
#include <vespa/vespalib/stllike/string.h>
#include "types.h"

namespace vespalib {
namespace tensor {

/**
 * Class for visiting a tensor.  First visit must specify dimensions,
 * remaining visits must specify tensor addresses and values.
 */
class TensorVisitor
{
public:
    virtual ~TensorVisitor() {}
    virtual void visit(const TensorAddress &address, double value) = 0;
};

} // namespace vespalib::tensor
} // namespace vespalib
