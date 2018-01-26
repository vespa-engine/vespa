// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/eval/eval/tensor_function.h>
#include <vespa/vespalib/hwaccelrated/iaccelrated.h>

namespace vespalib::tensor {

/**
 * Tensor function for a dot product between two 1-dimensional dense tensors.
 */
class DenseDotProductFunction : public eval::TensorFunction
{
private:
    size_t _lhsTensorId;
    size_t _rhsTensorId;
    hwaccelrated::IAccelrated::UP _hwAccelerator;

public:
    DenseDotProductFunction(size_t lhsTensorId_, size_t rhsTensorId_);
    size_t lhsTensorId() const { return _lhsTensorId; }
    size_t rhsTensorId() const { return _rhsTensorId; }
    const eval::ValueType &result_type() const override { return eval::DoubleValue::double_type(); }
    void push_children(std::vector<Child::CREF> &) const override {}
    eval::InterpretedFunction::Instruction compile_self(Stash &stash) const override;
    const eval::Value &eval(const eval::TensorEngine &engine, const eval::LazyParams &params, Stash &stash) const override;
};

}

