// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/eval/eval/value.h>
#include <vespa/eval/eval/value_type.h>

namespace vespalib::tensor {

/**
 * A factory that can generate ValueBuilder
 * objects appropriate for the requested type.
 */
struct DefaultValueBuilderFactory : eval::ValueBuilderFactory {
private:
    DefaultValueBuilderFactory();
    static DefaultValueBuilderFactory _factory;
    ~DefaultValueBuilderFactory() override {}
protected:
    std::unique_ptr<eval::ValueBuilderBase> create_value_builder_base(const eval::ValueType &type,
            size_t num_mapped_in, size_t subspace_size_in, size_t expect_subspaces) const override;
public:
    static const DefaultValueBuilderFactory &get() { return _factory; }
};

} // namespace
