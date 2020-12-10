// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "streamed_value_builder_factory.h"
#include "streamed_value_builder.h"

namespace vespalib::eval {

struct SelectStreamedValueBuilder {
    template <typename T>
    static std::unique_ptr<ValueBuilderBase> invoke(
        const ValueType &type, size_t num_mapped, 
        size_t subspace_size, size_t expected_subspaces)
    {
        assert(check_cell_type<T>(type.cell_type()));
        return std::make_unique<StreamedValueBuilder<T>>(
            type, num_mapped, subspace_size, expected_subspaces);
    }
};

std::unique_ptr<ValueBuilderBase>
StreamedValueBuilderFactory::create_value_builder_base(const ValueType &type,
                                                       bool transient,
                                                       size_t num_mapped,
                                                       size_t subspace_size,
                                                       size_t expected_subspaces) const
{
    (void) transient;
    return typify_invoke<1,TypifyCellType,SelectStreamedValueBuilder>(
        type.cell_type(),
        type, num_mapped, subspace_size, expected_subspaces);
}

StreamedValueBuilderFactory::~StreamedValueBuilderFactory() = default;
StreamedValueBuilderFactory StreamedValueBuilderFactory::_factory;

} // namespace


