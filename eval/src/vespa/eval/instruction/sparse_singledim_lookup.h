// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/eval/eval/tensor_function.h>

namespace vespalib::eval {

/**
 * Look up the result of an expression (double->int64_t->label_enum)
 * in a sparse tensor with a single dimension, resulting in a double
 * result. If lookup keys are kept small [0,10000000> (to avoid label
 * enumeration) this is a simple hashtable lookup with numeric keys.
 **/
class SparseSingledimLookup : public tensor_function::Op2
{
public:
    SparseSingledimLookup(const TensorFunction &tensor, const TensorFunction &expr);
    InterpretedFunction::Instruction compile_self(const ValueBuilderFactory &factory, Stash &stash) const override;
    bool result_is_mutable() const override { return true; }
    static const TensorFunction &optimize(const TensorFunction &expr, Stash &stash);
};

} // namespace
