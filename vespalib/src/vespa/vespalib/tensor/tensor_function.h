// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "tensor_type.h"
#include "tensor.h"
#include <memory>

namespace vespalib {
namespace tensor {

//-----------------------------------------------------------------------------

/**
 * A tensor function that can be evaluated. A TensorFunction will
 * typically be produced by an implementation-specific compile step
 * that takes an implementation-independent intermediate
 * representation of the tensor function as input (tree of
 * function::Node objects).
 **/
struct TensorFunction
{
    typedef std::unique_ptr<TensorFunction> UP;

    /**
     * A tensor function will evaluate to either a tensor or a double
     * value. The result type indicated by the tensor function
     * intermediate representation will indicate which form is valid.
     **/
    union Result {
        double       as_double;
        Tensor::CREF as_tensor;
        Result(const Result &rhs) { memcpy(this, &rhs, sizeof(Result)); }
        Result(double value) : as_double(value) {}
        Result(const Tensor &value) : as_tensor(value) {}
        ~Result() {}
    };

    /**
     * Interface used to obtain input to a tensor function.
     **/
    struct Input {
        virtual const Tensor &get_tensor(size_t id) const = 0;
        virtual const CellFunction &get_cell_function(size_t id) const = 0;
        virtual ~Input() {}
    };

    /**
     * Evaluate this tensor function based on the given input. This
     * function is defined as non-const because it will return tensors
     * by reference. Intermediate results are typically kept alive
     * until the next time eval is called. The return value must
     * conform to the result type indicated by the intermediate
     * representation describing this tensor function.
     *
     * @return result of evaluating this tensor function
     * @param input external stuff needed to evaluate this function
     **/
    virtual Result eval(const Input &input) = 0;

    virtual ~TensorFunction() {}
};

//-----------------------------------------------------------------------------

namespace function {

/**
 * Interface used to describe a tensor function as a tree of nodes
 * with information about operation sequencing and intermediate result
 * types. Each node in the tree will describe a single tensor
 * operation. This is the intermediate representation of a tensor
 * function.
 *
 * Since tensor operations currently are part of the tensor interface,
 * the intermediate representation of a tensor function can also be
 * used to evaluate the tensor function by performing the appropriate
 * operations directly on the input tensors. In other words, the
 * intermediate representation 'compiles to itself'.
 **/
struct Node : public TensorFunction
{
    /**
     * The result type of the tensor operation represented by this
     * Node.
     *
     * @return tensor operation result type.
     **/
    virtual const eval::ValueType &type() const = 0;
};

using Node_UP = std::unique_ptr<Node>;

Node_UP input(const eval::ValueType &type, size_t tensor_id);
Node_UP sum(Node_UP child);
Node_UP dimension_sum(Node_UP child, const vespalib::string &dimension);
Node_UP apply(Node_UP child, size_t cell_function_id);
Node_UP add(Node_UP lhs, Node_UP rhs);
Node_UP subtract(Node_UP lhs, Node_UP rhs);
Node_UP multiply(Node_UP lhs, Node_UP rhs);
Node_UP min(Node_UP lhs, Node_UP rhs);
Node_UP max(Node_UP lhs, Node_UP rhs);
Node_UP match(Node_UP lhs, Node_UP rhs);

} // namespace vespalib::tensor::function

//-----------------------------------------------------------------------------

} // namespace vespalib::tensor
} // namespace vespalib
