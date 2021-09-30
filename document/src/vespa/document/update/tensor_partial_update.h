// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/eval/eval/fast_value.h>
#include <vespa/eval/eval/value.h>
#include <vespa/eval/eval/operation.h>

namespace document {

struct TensorPartialUpdate {
    using join_fun_t = vespalib::eval::operation::op2_t;
    using Value = vespalib::eval::Value;
    using ValueBuilderFactory = vespalib::eval::ValueBuilderFactory;

    /**
     *  Make a copy of the input, but apply function(oldvalue, modifier.cellvalue)
     *  to cells which also exist in the "modifier".
     *  The modifier type must be sparse with exactly the same dimension names
     *  as the input type.
     *  Returns null pointer if this constraint is violated.
     **/
    static Value::UP modify(const Value &input, join_fun_t function,
                            const Value &modifier, const ValueBuilderFactory &factory);

    /**
     *  Make a copy of the input, but add or overwrite cells from add_cells.
     *  Requires same type for input and add_cells.
     *  Returns null pointer if this constraint is violated.
     **/
    static Value::UP add(const Value &input, const Value &add_cells, const ValueBuilderFactory &factory);

    /**
     *  Make a copy of the input, but remove cells present in remove_spec.
     *  The remove_spec must be a sparse tensor, with exactly the mapped dimensions
     *  that the input value has.
     *  Cell values in remove_spec are ignored.
     *  Not valid for dense tensors, since removing cells for those are impossible.
     *  Returns null pointer if these constraints are violated.
     **/
    static Value::UP remove(const Value &input, const Value &remove_spec, const ValueBuilderFactory &factory);
};

} // namespace
