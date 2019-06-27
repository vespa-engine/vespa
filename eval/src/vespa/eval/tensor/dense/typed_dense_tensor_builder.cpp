

#include "typed_dense_tensor_builder.h"

namespace vespalib::tensor {

size_t num_typed_tensor_builder_inserts = 0;

using Address = DenseTensorView::Address;
using eval::ValueType;

namespace {

size_t
calculateCellsSize(const ValueType &type)
{
    size_t cellsSize = 1;
    for (const auto &dim : type.dimensions()) {
        cellsSize *= dim.size;
    }
    return cellsSize;
}

} // namespace

template <typename CT>
TypedDenseTensorBuilder<CT>::~TypedDenseTensorBuilder() = default;

template <typename CT>
TypedDenseTensorBuilder<CT>::TypedDenseTensorBuilder(const ValueType &type_in)
    : _type(type_in),
      _cells(calculateCellsSize(_type))
{
}

template <typename CT>
Tensor::UP
TypedDenseTensorBuilder<CT>::build()
{
    return std::make_unique<DenseTensor<CT>>(std::move(_type), std::move(_cells));
}

template class TypedDenseTensorBuilder<double>;
template class TypedDenseTensorBuilder<float>;

} // namespace
