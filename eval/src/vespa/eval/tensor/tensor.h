// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "cell_function.h"
#include "tensor_address.h"
#include <vespa/vespalib/stllike/string.h>
#include <vespa/eval/eval/tensor.h>
#include <vespa/eval/eval/tensor_spec.h>
#include <vespa/eval/eval/value_type.h>

namespace vespalib::eval { struct BinaryOperation; }
namespace vespalib::tensor {

class TensorVisitor;
class CellValues;

/**
 * Interface for operations on a tensor (sparse multi-dimensional array).
 *
 * A sparse tensor is a set of cells containing scalar values.
 * Each cell is identified by its address, which consists of a set of dimension -> label pairs,
 * where both dimension and label is a string on the form of an identifier or integer.
 */
class Tensor : public eval::Tensor
{
public:
    typedef std::unique_ptr<Tensor> UP;
    typedef std::reference_wrapper<const Tensor> CREF;
    using join_fun_t = double (*)(double, double);

    Tensor();
    virtual ~Tensor() {}
    virtual Tensor::UP apply(const CellFunction &func) const = 0;
    virtual Tensor::UP join(join_fun_t function, const Tensor &arg) const = 0;
    virtual Tensor::UP merge(join_fun_t function, const Tensor &arg) const = 0;
    virtual Tensor::UP reduce(join_fun_t op, const std::vector<vespalib::string> &dimensions) const = 0;

    /*
     * Creates a new tensor by modifying the underlying cells matching
     * the given cells applying a join function to determine the new
     * cell value.
     */
    virtual std::unique_ptr<Tensor> modify(join_fun_t op, const CellValues &cellValues) const = 0;

    /**
     * Creates a new tensor by adding the cells of the argument tensor to this tensor.
     * Existing cell values are overwritten.
     */
    virtual std::unique_ptr<Tensor> add(const Tensor &arg) const = 0;

    /**
     * Creates a new tensor by removing the cells matching the given cell addresses.
     * The value associated with the address is ignored.
     */
    virtual std::unique_ptr<Tensor> remove(const CellValues &cellAddresses) const = 0;

    virtual bool equals(const Tensor &arg) const = 0; // want to remove, but needed by document
    virtual Tensor::UP clone() const = 0; // want to remove, but needed by document
    virtual eval::TensorSpec toSpec() const = 0;
    virtual void accept(TensorVisitor &visitor) const = 0;

    using TypeList = std::initializer_list<std::reference_wrapper<const eval::ValueType>>;
    static bool supported(TypeList types);
};

std::ostream &operator<<(std::ostream &out, const Tensor &value);

}
