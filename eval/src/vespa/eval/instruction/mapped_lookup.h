// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/eval/eval/tensor_function.h>

namespace vespalib::eval {

/**
 * Tensor function implementing generalized lookup of 'key' in 'map'
 * with some type restrictions.
 *
 * 'key' may only contain the lookup dimension (called 'x' here)
 * 'map' must have full mapped overlap with 'key'
 *
 * Both tensors must have the same cell type, which can be either
 * float or double.
 * 
 * The optimized expression looks like this: reduce(key*map,sum,x)
 *
 * If 'map' is also sparse, the lookup operation is a sparse dot
 * product and will be optimized using SparseDotProductFunction
 * instead.
 *
 * The best performance (simple hash lookup with a result referencing
 * existing cells without having to copy them) is achieved when a
 * single dense subspace in 'map' matches a cell with value 1.0 from
 * 'key'. This fast-path can be ensured if this optimization is
 * combined with the simple_join_count optimization:
 *
 * key = tensor(x{}):{my_key:1}
 * map = tensor(x{},y[128])
 * fallback = tensor(y[128])
 *
 * simple lookup with fallback:
 * if(reduce(key*map,count)==128,reduce(key*map,sum,x),fallback)
 **/
class MappedLookup : public tensor_function::Op2
{
public:
    MappedLookup(const ValueType &res_type, const TensorFunction &key_in, const TensorFunction &map_in);
    const TensorFunction &key() const { return lhs(); }
    const TensorFunction &map() const { return rhs(); }
    InterpretedFunction::Instruction compile_self(const ValueBuilderFactory &factory, Stash &stash) const override;
    bool result_is_mutable() const override { return map().result_is_mutable(); }
    static const TensorFunction &optimize(const TensorFunction &expr, Stash &stash);
};

} // namespace
