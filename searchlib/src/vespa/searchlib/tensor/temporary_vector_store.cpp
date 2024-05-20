// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "temporary_vector_store.h"
#include <vespa/vespalib/hwaccelrated/iaccelrated.h>

using vespalib::ConstArrayRef;
using vespalib::ArrayRef;
using vespalib::eval::CellType;
using vespalib::eval::TypedCells;
using vespalib::hwaccelrated::IAccelrated;

namespace search::tensor {

namespace {

template<typename FromType, typename ToType>
ConstArrayRef<ToType>
convert_cells(ArrayRef<ToType> space, TypedCells cells) noexcept __attribute__((noinline));

template<typename FromType, typename ToType>
ConstArrayRef<ToType>
convert_cells(ArrayRef<ToType> space, TypedCells cells) noexcept
{
    auto old_cells = cells.unsafe_typify<FromType>();
    ToType *p = space.data();
    for (FromType value : old_cells) {
        *p++ = static_cast<ToType>(value);
    }
    return space;
}

template<>
ConstArrayRef<float>
convert_cells<vespalib::BFloat16, float>(ArrayRef<float> space, TypedCells cells) noexcept
{
    static const IAccelrated & accelerator = IAccelrated::getAccelerator();
    accelerator.convert_bfloat16_to_float(reinterpret_cast<const uint16_t *>(cells.data), space.data(), space.size());
    return space;
}

template <typename ToType>
struct ConvertCellsSelector
{
    template <typename FromType> static auto invoke(ArrayRef<ToType> dst, TypedCells src) noexcept {
        return convert_cells<FromType, ToType>(dst, src);
    }
};

} // namespace

template <typename FloatType>
ConstArrayRef<FloatType>
TemporaryVectorStore<FloatType>::internal_convert(TypedCells cells, size_t offset) noexcept {
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
