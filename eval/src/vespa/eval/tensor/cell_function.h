// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <functional>

namespace vespalib {
namespace tensor {

/**
 * Interface for a function to be applied on cells in a tensor.
 */
struct CellFunction
{
    typedef std::reference_wrapper<const CellFunction> CREF;
    virtual ~CellFunction() {}
    virtual double apply(double value) const = 0;
};

} // namespace vespalib::tensor
} // namespace vespalib
