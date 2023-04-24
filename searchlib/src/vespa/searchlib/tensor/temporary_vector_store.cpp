// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "temporary_vector_store.h"

#include <vespa/log/log.h>

LOG_SETUP(".searchlib.tensor.temporary_vector_store");

using vespalib::ConstArrayRef;
using vespalib::ArrayRef;
using vespalib::eval::CellType;
using vespalib::eval::TypedCells;

namespace search::tensor {

namespace {

template<typename FromType, typename ToType>
ConstArrayRef<ToType>
convert_cells(ArrayRef<ToType> space, TypedCells cells)
{
    assert(cells.size == space.size());
    auto old_cells = cells.typify<FromType>();
    ToType *p = space.data();
    for (FromType value : old_cells) {
        ToType conv(value);
        *p++ = conv;
    }
    return space;
}

template <typename ToType>
struct ConvertCellsSelector
{
    template <typename FromType> static auto invoke(ArrayRef<ToType> dst, TypedCells src) {
        return convert_cells<FromType, ToType>(dst, src);
    }
};

} // namespace

template <typename FloatType>
ConstArrayRef<FloatType>
TemporaryVectorStore<FloatType>::internal_convert(TypedCells cells, size_t offset) {
    LOG_ASSERT(cells.size * 2 == _tmpSpace.size());
    ArrayRef<FloatType> where(_tmpSpace.data() + offset, cells.size);
    using MyTypify = vespalib::eval::TypifyCellType;
    using MySelector = ConvertCellsSelector<FloatType>;
    ConstArrayRef<FloatType> result = vespalib::typify_invoke<1,MyTypify,MySelector>(cells.type, where, cells);
    return result;
}

template class TemporaryVectorStore<vespalib::eval::Int8Float>;
template class TemporaryVectorStore<float>;
template class TemporaryVectorStore<double>;

}
