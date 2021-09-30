// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "value.h"

namespace vespalib::eval {

/**
 * A fast value is a value that uses a FastValueIndex to store its
 * sparse mappings. The FastValueIndex class contains inlined
 * functions that can be called directly from instruction
 * implementations to speed up sparse operations. Also, the
 * FastSparseMap used by the FastValueIndex is a highly optimized
 * alternative to the map used by SimpleValue, which means that normal
 * Value API usage will also have improved performance.
 **/
class FastValueBuilderFactory : public ValueBuilderFactory {
private:
    FastValueBuilderFactory();
    static FastValueBuilderFactory _factory;
    std::unique_ptr<ValueBuilderBase> create_value_builder_base(const ValueType &type, bool transient,
            size_t num_mapped_dims, size_t subspace_size, size_t expected_subspaces) const override;
public:
    static const FastValueBuilderFactory &get() { return _factory; }
};

}
