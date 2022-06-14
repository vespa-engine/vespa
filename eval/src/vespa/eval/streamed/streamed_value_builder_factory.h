// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "streamed_value.h"
#include <vespa/eval/eval/value_builder_factory.h>

namespace vespalib::eval {

/**
 * A factory that can generate appropriate ValueBuilder instances
 */
struct StreamedValueBuilderFactory : ValueBuilderFactory {
private:
    StreamedValueBuilderFactory() {}
    static StreamedValueBuilderFactory _factory;
    std::unique_ptr<ValueBuilderBase> create_value_builder_base(
        const ValueType &type, bool transient, size_t num_mapped_in,
        size_t subspace_size_in, size_t expected_subspaces) const override;
public:
    static const StreamedValueBuilderFactory &get() { return _factory; }
    ~StreamedValueBuilderFactory() override;
};

}
