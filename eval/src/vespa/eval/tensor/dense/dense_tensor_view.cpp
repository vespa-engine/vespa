// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "dense_tensor_view.h"
#include "dense_generic_join.hpp"
#include "dense_tensor_reduce.hpp"
#include "dense_tensor_modify.h"
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <vespa/eval/tensor/cell_values.h>
#include <vespa/eval/tensor/tensor_address_builder.h>
#include <vespa/eval/tensor/tensor_visitor.h>
#include <vespa/eval/eval/operation.h>
#include <sstream>

#include <vespa/log/log.h>
LOG_SETUP(".eval.tensor.dense.dense_tensor_view");

using vespalib::eval::TensorSpec;

namespace vespalib::tensor {

namespace {

string
dimensionsAsString(const eval::ValueType &type)
{
    std::ostringstream oss;
    bool first = true;
    oss << "[";
    for (const auto &dim : type.dimensions()) {
        if (!first) {
            oss << ",";
        }
        first = false;
        oss << dim.name << ":" << dim.size;
    }
    oss << "]";
    return oss.str();
}

void
checkCellsSize(const eval::ValueType &type, TypedCells cells)
{
    auto cellsSize = type.dense_subspace_size();
    if (cells.size != cellsSize) {
        throw IllegalStateException(make_string("wrong cell size, "
                                                "expected=%zu, "
                                                "actual=%zu",
                                                cellsSize,
                                                cells.size));
    }
}

void
checkDimensions(const eval::ValueType &lhs, const eval::ValueType &rhs,
                vespalib::stringref operation)
{
    if (lhs.dimensions() != rhs.dimensions()) {
        throw IllegalStateException(make_string("mismatching dimensions for "
                                                "dense tensor %s, "
                                                "lhs dimensions = '%s', "
                                                "rhs dimensions = '%s'",
                                                operation.data(),
                                                dimensionsAsString(lhs).c_str(),
                                                dimensionsAsString(rhs).c_str()));
    }
}

/*
 * Join the cells of two tensors.
 *
 * The given function is used to calculate the resulting cell value
 * for overlapping cells.
 */
template <typename LCT, typename RCT, typename Function>
static Tensor::UP
sameShapeJoin(const ConstArrayRef<LCT> &lhs, const ConstArrayRef<RCT> &rhs,
              const std::vector<eval::ValueType::Dimension> &lhs_dims,
              Function &&func)
{
    size_t sz = lhs.size();
    assert(sz == rhs.size());
    using OCT = typename eval::UnifyCellTypes<LCT,RCT>::type;
    std::vector<OCT> newCells;
    newCells.reserve(sz);
    auto rhsCellItr = rhs.cbegin();
    for (const auto &lhsCell : lhs) {
        OCT v = func(lhsCell, *rhsCellItr);
        newCells.push_back(v);
        ++rhsCellItr;
    }
    assert(rhsCellItr == rhs.cend());
    assert(newCells.size() == sz);
    auto newType = eval::ValueType::tensor_type(lhs_dims, eval::get_cell_type<OCT>());
    return std::make_unique<DenseTensor<OCT>>(std::move(newType), std::move(newCells));
}

struct CallJoin
{
    template <typename LCT, typename RCT, typename Function>
    static Tensor::UP
    call(const ConstArrayRef<LCT> &lhs, const ConstArrayRef<RCT> &rhs,
         const std::vector<eval::ValueType::Dimension> &lhs_dims,
         Function &&func)
    {
        return sameShapeJoin(lhs, rhs, lhs_dims, std::move(func));
    }
};

template <typename Function>
Tensor::UP
joinDenseTensors(const DenseTensorView &lhs, const Tensor &rhs,
                 vespalib::stringref operation,
                 Function &&func)
{
    const auto & lhs_type = lhs.fast_type();
    const auto & rhs_type = rhs.type();
    TypedCells lhs_cells = lhs.cells();
    TypedCells rhs_cells = rhs.cells();
    checkDimensions(lhs_type, rhs_type, operation);
    checkCellsSize(lhs_type, lhs_cells);
    checkCellsSize(rhs_type, rhs_cells);
    return dispatch_2<CallJoin>(lhs_cells, rhs_cells, lhs_type.dimensions(), std::move(func));
}

bool sameCells(TypedCells lhs, TypedCells rhs)
{
    if (lhs.size != rhs.size) {
        return false;
    }
    for (size_t i = 0; i < lhs.size; ++i) {
        if (GetCell::from(lhs, i) != GetCell::from(rhs, i)) {
            return false;
        }
    }
    return true;
}

}

bool
DenseTensorView::operator==(const DenseTensorView &rhs) const
{
    return (_typeRef == rhs._typeRef) && sameCells(_cellsRef, rhs._cellsRef);
}

const eval::ValueType &
DenseTensorView::type() const
{
    return _typeRef;
}

struct CallSum {
    template <typename CT>
    static double
    call(const ConstArrayRef<CT> &arr) {
        double res = 0.0;
        for (CT val : arr) {
            res += val;
        }
        return res;
    }
};

double
DenseTensorView::as_double() const
{
    return dispatch_1<CallSum>(_cellsRef);
}

struct CallApply {
    template <typename CT>
    static Tensor::UP
    call(const ConstArrayRef<CT> &oldCells, const eval::ValueType &newType, const CellFunction &func)
    {
        std::vector<CT> newCells;
        newCells.reserve(oldCells.size());
        for (const auto &cell : oldCells) {
            CT nv = func.apply(cell);
            newCells.push_back(nv);
        }
        return std::make_unique<DenseTensor<CT>>(newType, std::move(newCells));
    }
};

Tensor::UP
DenseTensorView::apply(const CellFunction &func) const
{
    return dispatch_1<CallApply>(_cellsRef, _typeRef, func);
}

bool
DenseTensorView::equals(const Tensor &arg) const
{
    if (fast_type() == arg.type()) {
        return sameCells(cells(), arg.cells());
    }
    return false;
}

struct CallClone {
    template<class CT>
    static Tensor::UP
    call(const ConstArrayRef<CT> &cells, eval::ValueType newType)
    {
        std::vector<CT> newCells(cells.begin(), cells.end());
        return std::make_unique<DenseTensor<CT>>(std::move(newType), std::move(newCells));
    }
};

Tensor::UP
DenseTensorView::clone() const
{
    return dispatch_1<CallClone>(_cellsRef, _typeRef);
}

namespace {

void
buildAddress(const DenseTensorCellsIterator &itr, TensorSpec::Address &address)
{
    auto addressItr = itr.address().begin();
    for (const auto &dim : itr.fast_type().dimensions()) {
        address.emplace(std::make_pair(dim.name, TensorSpec::Label(*addressItr++)));
    }
    assert(addressItr == itr.address().end());
}

}

TensorSpec
DenseTensorView::toSpec() const
{
    TensorSpec result(type().to_spec());
    TensorSpec::Address address;
    for (CellsIterator itr(_typeRef, _cellsRef); itr.valid(); itr.next()) {
        buildAddress(itr, address);
        result.add(address, itr.cell());
        address.clear();
    }
    return result;
}

void
DenseTensorView::accept(TensorVisitor &visitor) const
{
    CellsIterator iterator(_typeRef, _cellsRef);
    TensorAddressBuilder addressBuilder;
    TensorAddress address;
    vespalib::string label;
    while (iterator.valid()) {
        addressBuilder.clear();
        auto rawIndex = iterator.address().begin();
        for (const auto &dimension : _typeRef.dimensions()) {
            label = vespalib::make_string("%u", *rawIndex);
            addressBuilder.add(dimension.name, label);
            ++rawIndex;
        }
        address = addressBuilder.build();
        visitor.visit(address, iterator.cell());
        iterator.next();
    }
}

Tensor::UP
DenseTensorView::join(join_fun_t function, const Tensor &arg) const
{
    if (fast_type().dimensions() == arg.type().dimensions()) {
        if (function == eval::operation::Mul::f) {
            return joinDenseTensors(*this, arg, "mul",
                                    [](double a, double b) { return (a * b); });
        }
        if (function == eval::operation::Add::f) {
            return joinDenseTensors(*this, arg, "add",
                                    [](double a, double b) { return (a + b); });
        }
        return joinDenseTensors(*this, arg, "join", function);
    }
    if (function == eval::operation::Mul::f) {
        return dense::generic_join(*this, arg, [](auto a, auto b) { return (a * b); });
    }
    if (function == eval::operation::Add::f) {
        return dense::generic_join(*this, arg, [](double a, double b) { return (a + b); });
    }
    return dense::generic_join(*this, arg, function);
}

Tensor::UP
DenseTensorView::merge(join_fun_t function, const Tensor &arg) const
{
    assert(fast_type().dimensions() == arg.type().dimensions());
    return join(function, arg);
}

Tensor::UP
DenseTensorView::reduce_all(join_fun_t op, const std::vector<vespalib::string> &dims) const
{
    if (op == eval::operation::Mul::f) {
        return dense::reduce(*this, dims, [](double a, double b) { return (a * b);});
    }
    if (op == eval::operation::Add::f) {
        return dense::reduce(*this, dims, [](auto a, auto b) { return (a + b);});
    }
    return dense::reduce(*this, dims, op);
}

Tensor::UP
DenseTensorView::reduce(join_fun_t op, const std::vector<vespalib::string> &dimensions) const
{
    return dimensions.empty()
            ? reduce_all(op, _typeRef.dimension_names())
            : reduce_all(op, dimensions);
}

struct CallModify
{
    using join_fun_t = vespalib::eval::operation::op2_t;

    template <typename CT>
    static std::unique_ptr<Tensor>
    call(const ConstArrayRef<CT> &arr, join_fun_t op, const eval::ValueType &typeRef, const CellValues &cellValues)
    {
        std::vector newCells(arr.begin(), arr.end());
        DenseTensorModify<CT> modifier(op, typeRef, std::move(newCells));
        cellValues.accept(modifier);
        return modifier.build();
    }
};

std::unique_ptr<Tensor>
DenseTensorView::modify(join_fun_t op, const CellValues &cellValues) const
{
    return dispatch_1<CallModify>(_cellsRef, op, _typeRef, cellValues);
}

std::unique_ptr<Tensor>
DenseTensorView::add(const Tensor &) const
{
    LOG_ABORT("should not be reached");
}

std::unique_ptr<Tensor>
DenseTensorView::remove(const CellValues &) const
{
    LOG_ABORT("should not be reached");
}

}
