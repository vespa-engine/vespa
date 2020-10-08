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
        return std::make_unique<FastValue<T>>(type, num_mapped_dims, subspace_size, expected_subspaces);
    }
};

} // namespace <unnamed>

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
