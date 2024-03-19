// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "benchmark_searchable.h"
#include "common.h"
#include <vespa/searchcommon/attribute/config.h>
#include <vespa/searchlib/test/mock_attribute_context.h>
#include <memory>

namespace search::queryeval::test {

/**
 * Class used to build attribute(s), used for benchmarking.
 */
class AttributeContextBuilder {
private:
    std::unique_ptr<search::attribute::test::MockAttributeContext> _ctx;

public:
    AttributeContextBuilder();
    void add(const search::attribute::Config& cfg, vespalib::stringref field_name, uint32_t num_docs, const HitSpecs& hit_specs);
    std::unique_ptr<BenchmarkSearchable> build();
};

}
