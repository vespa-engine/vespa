// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "fast_value.h"
#include <vespa/vespalib/util/typify.h>
#include "fast_value.hpp"

namespace vespalib::eval {

//-----------------------------------------------------------------------------

namespace {

struct CreateFastValueBuilderBase {
    template <typename T> static std::unique_ptr<ValueBuilderBase> invoke(const ValueType &type,
            size_t num_mapped_dims, size_t subspace_size, size_t expected_subspaces)
    {
        assert(check_cell_type<T>(type.cell_type()));
        if (type.is_scalar()) {
            return std::make_unique<FastScalarBuilder<T>>();
        } else if (num_mapped_dims == 0) {
            return std::make_unique<FastDenseValue<T>>(type, subspace_size);
        } else {
            return std::make_unique<FastValue<T>>(type, num_mapped_dims, subspace_size, expected_subspaces);
        }
    }
};

} // namespace <unnamed>

//-----------------------------------------------------------------------------

std::unique_ptr<Value::Index::View>
FastValueIndex::create_view(const std::vector<size_t> &dims) const
{
    if (map.num_dims() == 0) {
        return TrivialIndex::get().create_view(dims);
    } else if (dims.empty()) {
        return std::make_unique<FastIterateView>(map);
    } else if (dims.size() == map.num_dims()) {
        return std::make_unique<FastLookupView>(map);
    } else {
        return std::make_unique<FastFilterView>(map, dims);
    }
}

//-----------------------------------------------------------------------------

FastValueBuilderFactory::FastValueBuilderFactory() = default;
FastValueBuilderFactory FastValueBuilderFactory::_factory;

std::unique_ptr<ValueBuilderBase>
FastValueBuilderFactory::create_value_builder_base(const ValueType &type, size_t num_mapped_dims, size_t subspace_size,
                                                     size_t expected_subspaces) const
{
    return typify_invoke<1,TypifyCellType,CreateFastValueBuilderBase>(type.cell_type(), type, num_mapped_dims, subspace_size, expected_subspaces);
}

//-----------------------------------------------------------------------------

}
