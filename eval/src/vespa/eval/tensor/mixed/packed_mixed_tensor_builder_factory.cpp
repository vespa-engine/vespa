// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "packed_mixed_tensor_builder_factory.h"
#include "packed_mixed_tensor_builder.h"

#include <vespa/vespalib/util/typify.h>

namespace vespalib::eval {

namespace {

struct CreatePackedMixedTensorBuilder {
    template <typename T, typename ...Args>
    static std::unique_ptr<ValueBuilderBase> invoke(const ValueType &type, Args &&...args)
    {
        assert(check_cell_type<T>(type.cell_type()));
        return std::make_unique<packed_mixed_tensor::PackedMixedTensorBuilder<T>>(type, std::forward<Args>(args)...);
    }
};

} // namespace <unnamed>

PackedMixedTensorBuilderFactory::PackedMixedTensorBuilderFactory() = default;
PackedMixedTensorBuilderFactory PackedMixedTensorBuilderFactory::_factory;

std::unique_ptr<ValueBuilderBase>
PackedMixedTensorBuilderFactory::create_value_builder_base(const ValueType &type,
                                              size_t num_mapped_in,
                                              size_t subspace_size_in,
                                              size_t expected_subspaces) const
{
    return typify_invoke<1,TypifyCellType,CreatePackedMixedTensorBuilder>(type.cell_type(),
                    type, num_mapped_in, subspace_size_in, expected_subspaces);
}

} // namespace
