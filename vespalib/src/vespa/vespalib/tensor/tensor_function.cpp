// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include "tensor_function.h"
#include <vespa/vespalib/eval/value_type.h>

namespace vespalib {
namespace tensor {
namespace function {
namespace {

//-----------------------------------------------------------------------------

/**
 * Base function class keeping track of result type.
 **/
class FunctionBase : public Node
{
private:
    eval::ValueType _type;
protected:
    explicit FunctionBase(const eval::ValueType &type_in) : _type(type_in) {}
    const eval::ValueType &type() const override { return _type; }

    // helper function used to unwrap tensor value from eval result
    static const Tensor &eval_tensor(Node &node, const Input &input) {
        return node.eval(input).as_tensor;
    }
};

//-----------------------------------------------------------------------------

/**
 * Function mixin class used to keep tensor results alive.
 **/
class TensorCache : public FunctionBase
{
private:
    Tensor::UP _my_result;
protected:
    explicit TensorCache(const eval::ValueType &type_in)
        : FunctionBase(type_in), _my_result() {}
    const Tensor &store_tensor(Tensor::UP result) {
        _my_result = std::move(result);
        return *_my_result;
    }
};

//-----------------------------------------------------------------------------

/**
 * Resolve an input tensor value.
 **/
class InputTensor : public FunctionBase
{
private:
    size_t _tensor_id;

    static eval::ValueType infer_type(const eval::ValueType &type_in) {
        if (type_in.is_tensor()) {
            return type_in;
        } else {
            return eval::ValueType::error_type();
        }
    }

public:
    InputTensor(const eval::ValueType &type_in, size_t tensor_id)
        : FunctionBase(infer_type(type_in)), _tensor_id(tensor_id) {}
    Result eval(const Input &input) override {
        return input.get_tensor(_tensor_id);
    }
};

//-----------------------------------------------------------------------------

/**
 * Sum all the cells in a tensor.
 **/
class Sum : public FunctionBase
{
private:
    Node_UP _child;

    static eval::ValueType infer_type(const eval::ValueType &child_type) {
        if (child_type.is_tensor()) {
            return eval::ValueType::double_type();
        } else {
            return eval::ValueType::error_type();
        }
    }

public:
    explicit Sum(Node_UP child)
        : FunctionBase(infer_type(child->type())),
          _child(std::move(child)) {}

    Result eval(const Input &input) override {
        return eval_tensor(*_child, input).sum();
    }
};

//-----------------------------------------------------------------------------

/**
 * Perform sum over a tensor dimension.
 **/
class DimensionSum : public TensorCache
{
private:
    Node_UP          _child;
    vespalib::string _dimension;

    static eval::ValueType infer_type(const eval::ValueType &child_type, const vespalib::string &dimension) {
        return child_type.remove_dimensions({dimension});
    }

public:
    DimensionSum(Node_UP child, const vespalib::string &dimension)
        : TensorCache(infer_type(child->type(), dimension)),
          _child(std::move(child)), _dimension(dimension) {}

    Result eval(const Input &input) override {
        return store_tensor(eval_tensor(*_child, input).sum(_dimension));
    }
};

//-----------------------------------------------------------------------------

/**
 * Apply a cell function to all cells in a tensor.
 **/
class Apply : public TensorCache
{
private:
    Node_UP _child;
    size_t  _cell_function_id;

    static eval::ValueType infer_type(const eval::ValueType &child_type) {
        if (child_type.is_tensor()) {
            return child_type;
        } else {
            return eval::ValueType::error_type();
        }
    }

public:
    Apply(Node_UP child, size_t cell_function_id)
        : TensorCache(infer_type(child->type())),
          _child(std::move(child)), _cell_function_id(cell_function_id) {}

    Result eval(const Input &input) override {
        const auto &cell_function = input.get_cell_function(_cell_function_id);
        return store_tensor(eval_tensor(*_child, input).apply(cell_function));
    }
};

//-----------------------------------------------------------------------------

/**
 * Add two tensors.
 **/
class Add : public TensorCache
{
private:
    Node_UP _lhs;
    Node_UP _rhs;

    static eval::ValueType infer_type(const eval::ValueType &lhs_type, const eval::ValueType &rhs_type) {
        return lhs_type.add_dimensions_from(rhs_type);
    }

public:
    Add(Node_UP lhs, Node_UP rhs)
        : TensorCache(infer_type(lhs->type(), rhs->type())),
          _lhs(std::move(lhs)), _rhs(std::move(rhs)) {}

    Result eval(const Input &input) override {
        return store_tensor(eval_tensor(*_lhs, input)
                            .add(eval_tensor(*_rhs, input)));
    }
};

//-----------------------------------------------------------------------------

/**
 * Subtract two tensors.
 **/
class Subtract : public TensorCache
{
private:
    Node_UP _lhs;
    Node_UP _rhs;

    static eval::ValueType infer_type(const eval::ValueType &lhs_type, const eval::ValueType &rhs_type) {
        return lhs_type.add_dimensions_from(rhs_type);
    }

public:
    Subtract(Node_UP lhs, Node_UP rhs)
        : TensorCache(infer_type(lhs->type(), rhs->type())),
          _lhs(std::move(lhs)), _rhs(std::move(rhs)) {}

    Result eval(const Input &input) override {
        return store_tensor(eval_tensor(*_lhs, input)
                            .subtract(eval_tensor(*_rhs, input)));
    }
};

//-----------------------------------------------------------------------------

/**
 * Multiply two tensors.
 **/
class Multiply : public TensorCache
{
private:
    Node_UP _lhs;
    Node_UP _rhs;

    static eval::ValueType infer_type(const eval::ValueType &lhs_type, const eval::ValueType &rhs_type) {
        return lhs_type.add_dimensions_from(rhs_type);
    }

public:
    Multiply(Node_UP lhs, Node_UP rhs)
        : TensorCache(infer_type(lhs->type(), rhs->type())),
          _lhs(std::move(lhs)), _rhs(std::move(rhs)) {}

    Result eval(const Input &input) override {
        return store_tensor(eval_tensor(*_lhs, input)
                            .multiply(eval_tensor(*_rhs, input)));
    }
};

//-----------------------------------------------------------------------------

/**
 * Cellwise min between two tensors.
 **/
class Min : public TensorCache
{
private:
    Node_UP _lhs;
    Node_UP _rhs;

    static eval::ValueType infer_type(const eval::ValueType &lhs_type, const eval::ValueType &rhs_type) {
        return lhs_type.add_dimensions_from(rhs_type);
    }

public:
    Min(Node_UP lhs, Node_UP rhs)
        : TensorCache(infer_type(lhs->type(), rhs->type())),
          _lhs(std::move(lhs)), _rhs(std::move(rhs)) {}

    Result eval(const Input &input) override {
        return store_tensor(eval_tensor(*_lhs, input)
                            .min(eval_tensor(*_rhs, input)));
    }
};

//-----------------------------------------------------------------------------

/**
 * Cellwise max between two tensors.
 **/
class Max : public TensorCache
{
private:
    Node_UP _lhs;
    Node_UP _rhs;

    static eval::ValueType infer_type(const eval::ValueType &lhs_type, const eval::ValueType &rhs_type) {
        return lhs_type.add_dimensions_from(rhs_type);
    }

public:
    Max(Node_UP lhs, Node_UP rhs)
        : TensorCache(infer_type(lhs->type(), rhs->type())),
          _lhs(std::move(lhs)), _rhs(std::move(rhs)) {}

    Result eval(const Input &input) override {
        return store_tensor(eval_tensor(*_lhs, input)
                            .max(eval_tensor(*_rhs, input)));
    }
};

//-----------------------------------------------------------------------------

/**
 * Match two tensors.
 **/
class Match : public TensorCache
{
private:
    Node_UP _lhs;
    Node_UP _rhs;

    static eval::ValueType infer_type(const eval::ValueType &lhs_type, const eval::ValueType &rhs_type) {
        return lhs_type.keep_dimensions_in(rhs_type);
    }

public:
    Match(Node_UP lhs, Node_UP rhs)
        : TensorCache(infer_type(lhs->type(), rhs->type())),
          _lhs(std::move(lhs)), _rhs(std::move(rhs)) {}

    Result eval(const Input &input) override {
        return store_tensor(eval_tensor(*_lhs, input)
                            .match(eval_tensor(*_rhs, input)));
    }
};

//-----------------------------------------------------------------------------

} // namespace vespalib::tensor::function::<unnamed>

Node_UP input(const eval::ValueType &type, size_t tensor_id) {
    return std::make_unique<InputTensor>(type, tensor_id);
}

Node_UP sum(Node_UP child) {
    return std::make_unique<Sum>(std::move(child));
}

Node_UP dimension_sum(Node_UP child, const vespalib::string &dimension) {
    return std::make_unique<DimensionSum>(std::move(child), dimension);
}

Node_UP apply(Node_UP child, size_t cell_function_id) {
    return std::make_unique<Apply>(std::move(child), cell_function_id);
}

Node_UP add(Node_UP lhs, Node_UP rhs) {
    return std::make_unique<Add>(std::move(lhs), std::move(rhs));
}

Node_UP subtract(Node_UP lhs, Node_UP rhs) {
    return std::make_unique<Subtract>(std::move(lhs), std::move(rhs));
}

Node_UP multiply(Node_UP lhs, Node_UP rhs) {
    return std::make_unique<Multiply>(std::move(lhs), std::move(rhs));
}

Node_UP min(Node_UP lhs, Node_UP rhs) {
    return std::make_unique<Min>(std::move(lhs), std::move(rhs));
}

Node_UP max(Node_UP lhs, Node_UP rhs) {
    return std::make_unique<Max>(std::move(lhs), std::move(rhs));
}

Node_UP match(Node_UP lhs, Node_UP rhs) {
    return std::make_unique<Match>(std::move(lhs), std::move(rhs));    
}

} // namespace vespalib::tensor::function
} // namespace vespalib::tensor
} // namespace vespalib
