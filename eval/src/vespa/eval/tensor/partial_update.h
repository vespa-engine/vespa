// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/eval/eval/value.h>

namespace vespalib::tensor {

struct TensorPartialUpdate {
    using join_fun_t = double (*)(double, double);
    using Value = vespalib::eval::Value;
    using ValueBuilderFactory = vespalib::eval::ValueBuilderFactory;

    // make a copy of the input, but apply function(oldvalue, modifier.cellvalue)
    // to cells which also exist in the "modifier".
    // modifier.type() must be sparse with exactly the same dimension names
    // as the input type.
    // returns null pointer if this constraint is violated.
    static Value::UP modify(const Value &input, join_fun_t function,
                            const Value &modifier, const ValueBuilderFactory &factory);

    // make a copy of the input, but add or overwrite cells from add_cells.
    // requires same type for input and add_cells.
    // returns null pointer if this constraint is violated.
    static Value::UP add(const Value &input, const Value &add_cells, const ValueBuilderFactory &factory);

    // make a copy of the input, but remove cells present in remove_spec.
    // cell values in remove_spec are ignored.
    // requires same set of mapped imensions input and remove_spec.
    // not valid for dense tensors, since removing cells for those are impossible.
    // returns null pointer if these constraints are violated.
    static Value::UP remove(const Value &input, const Value &remove_spec, const ValueBuilderFactory &factory);
};

} // namespace
