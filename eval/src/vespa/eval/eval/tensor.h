// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "value_type.h"
#include "value.h"

namespace vespalib {
namespace eval {

struct TensorEngine;

/**
 * Base class for all tensors. Tensor operations are defined by the
 * TensorEngine interface. The Tensor class itself is used as a tagged
 * transport mechanism. Each Tensor is connected to a distinct engine
 * which can be used to operate on it. When operating on multiple
 * tensors at the same time they all need to be connected to the same
 * engine. TensorEngines should only have a single static instance per
 * implementation.
 **/
class Tensor : public Value
{
private:
    const TensorEngine &_engine;
protected:
    explicit Tensor(const TensorEngine &engine_in)
        : _engine(engine_in) {}
public:
    Tensor(const Tensor &) = delete;
    Tensor(Tensor &&) = delete;
    Tensor &operator=(const Tensor &) = delete;
    Tensor &operator=(Tensor &&) = delete;
    bool is_tensor() const override { return true; }
    const Tensor *as_tensor() const override { return this; }
    const TensorEngine &engine() const { return _engine; }
    virtual ~Tensor() {}
};

bool operator==(const Tensor &lhs, const Tensor &rhs);
std::ostream &operator<<(std::ostream &out, const Tensor &tensor);

} // namespace vespalib::eval
} // namespace vespalib
