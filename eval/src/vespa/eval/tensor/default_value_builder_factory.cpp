// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "default_value_builder_factory.h"
#include <vespa/vespalib/util/typify.h>
#include <vespa/eval/eval/value.h>
#include <vespa/eval/eval/double_value_builder.h>
#include <vespa/eval/tensor/dense/dense_tensor_value_builder.h>
#include <vespa/eval/tensor/mixed/packed_mixed_tensor_builder.h>
#include <vespa/eval/tensor/sparse/sparse_tensor_value_builder.h>

using namespace vespalib::eval;

namespace vespalib::tensor {

//-----------------------------------------------------------------------------

namespace {

struct CreateDefaultValueBuilderBase {
    template <typename T> static std::unique_ptr<ValueBuilderBase> invoke(const ValueType &type,
                                                                          size_t num_mapped_dims,
                                                                          size_t subspace_size,
                                                                          size_t expected_subspaces)
    {
        assert(check_cell_type<T>(type.cell_type()));
        if (type.is_double()) {
            return std::make_unique<DoubleValueBuilder>();
        }
        if (num_mapped_dims == 0) {
            return std::make_unique<DenseTensorValueBuilder<T>>(type, subspace_size);
        }
        if (subspace_size == 1) {
            return std::make_unique<SparseTensorValueBuilder<T>>(type, num_mapped_dims, expected_subspaces);
        }
        return std::make_unique<packed_mixed_tensor::PackedMixedTensorBuilder<T>>(type, num_mapped_dims, subspace_size, expected_subspaces);
    }
};

} // namespace <unnamed>

//-----------------------------------------------------------------------------

DefaultValueBuilderFactory::DefaultValueBuilderFactory() = default;
DefaultValueBuilderFactory DefaultValueBuilderFactory::_factory;

std::unique_ptr<ValueBuilderBase>
DefaultValueBuilderFactory::create_value_builder_base(const ValueType &type,
                                                      size_t num_mapped_dims,
                                                      size_t subspace_size,
                                                      size_t expected_subspaces) const
{
    return typify_invoke<1,TypifyCellType,CreateDefaultValueBuilderBase>(type.cell_type(), type, num_mapped_dims, subspace_size, expected_subspaces);
}

//-----------------------------------------------------------------------------

}
