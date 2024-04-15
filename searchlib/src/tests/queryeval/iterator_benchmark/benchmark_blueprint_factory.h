// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "common.h"
#include <memory>

namespace search::queryeval { class Blueprint; }

namespace search::queryeval::test {

/**
 * Interface for creating a Blueprint.
 */
class BenchmarkBlueprintFactory {
public:
    virtual ~BenchmarkBlueprintFactory() = default;
    virtual std::unique_ptr<Blueprint> make_blueprint() = 0;
    virtual vespalib::string get_name(Blueprint& blueprint) const = 0;
};

std::unique_ptr<BenchmarkBlueprintFactory>
make_blueprint_factory(const FieldConfig& field_cfg, QueryOperator query_op,
                       uint32_t num_docs, uint32_t default_values_per_document,
                       double op_hit_ratio, uint32_t children, bool disjunct_children);

}
