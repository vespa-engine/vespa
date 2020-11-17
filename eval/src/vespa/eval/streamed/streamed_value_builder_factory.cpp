// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "streamed_value_builder_factory.h"
#include "streamed_value_builder.h"

namespace vespalib::eval {

struct SelectStreamedValueBuilder {
    template <typename T, typename ...Args>
    static std::unique_ptr<ValueBuilderBase> invoke(const ValueType &type, Args &&...args)
    {
        assert(check_cell_type<T>(type.cell_type()));
        return std::make_unique<StreamedValueBuilder<T>>(type, std::forward<Args>(args)...);
    }
};

std::unique_ptr<ValueBuilderBase>
StreamedValueBuilderFactory::create_value_builder_base(const ValueType &type,
                                                       size_t num_mapped_in,
                                                       size_t subspace_size_in,
                                                       size_t expected_subspaces) const
{
    return typify_invoke<1,TypifyCellType,SelectStreamedValueBuilder>(
        type.cell_type(),
        type, num_mapped_in, subspace_size_in, expected_subspaces);
}

StreamedValueBuilderFactory::~StreamedValueBuilderFactory() = default;
StreamedValueBuilderFactory StreamedValueBuilderFactory::_factory;

} // namespace


