// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/eval/eval/value.h>
#include <vespa/eval/eval/value_type.h>
#include <vespa/eval/eval/simple_value.h>

namespace vespalib::eval {

/**
 * A factory that can generate PackedMixedTensorBuilder
 * objects appropriate for the requested CellType.
 */
struct PackedMixedTensorBuilderFactory : ValueBuilderFactory {
    ~PackedMixedTensorBuilderFactory() override {}
protected:
    std::unique_ptr<ValueBuilderBase> create_value_builder_base(const ValueType &type,
            size_t num_mapped_in, size_t subspace_size_in, size_t expect_subspaces) const override;
};

} // namespace
