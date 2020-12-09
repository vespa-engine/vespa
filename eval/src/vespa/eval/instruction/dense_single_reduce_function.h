// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/eval/eval/tensor_function.h>

namespace vespalib::eval {

struct DenseSingleReduceSpec {
    ValueType result_type;
    size_t outer_size;
    size_t reduce_size;
    size_t inner_size;
    Aggr aggr;
};

/**
 * Decompose the specified reduce operation into a sequence of single
 * dense reduce operations. Returns an empty list if decomposition
 * fails.
 **/
std::vector<DenseSingleReduceSpec>
make_dense_single_reduce_list(const ValueType &type, Aggr aggr,
                              const std::vector<vespalib::string> &reduce_dims);

/**
 * Tensor function reducing a single dimension of a dense tensor where
 * the result is also a dense tensor. The optimize function may create
 * multiple tensor functions to compose a multi-stage reduce
 * operation. Adjacent reduced dimensions will be handled is if they
 * were a single dimension. Trivial dimensions will be trivially
 * reduced along with any other dimension.
 * TODO: consider if we should extend this to handling mixed tensors
 * (handling the spare part as a batch dimension).
 **/
class DenseSingleReduceFunction : public tensor_function::Op1
{
private:
    size_t _outer_size;
    size_t _reduce_size;
    size_t _inner_size;
    Aggr _aggr;

public:
    DenseSingleReduceFunction(const DenseSingleReduceSpec &spec,
                              const TensorFunction &child);
    ~DenseSingleReduceFunction() override;
    size_t outer_size() const { return _outer_size; }
    size_t reduce_size() const { return _reduce_size; }
    size_t inner_size() const { return _inner_size; }
    Aggr aggr() const { return _aggr; }
    bool result_is_mutable() const override { return true; }
    InterpretedFunction::Instruction compile_self(const ValueBuilderFactory &factory, Stash &stash) const override;
    static const TensorFunction &optimize(const TensorFunction &expr, Stash &stash);
};

} // namespace vespalib::eval
