// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "blueprint_factory_builder.h"
#include <vespa/eval/eval/value.h>

namespace search::queryeval::test {

using vespalib::eval::Value;

/**
 * Factory that creates an ENN blueprint.
 */
class EnnBlueprintFactory : public BenchmarkBlueprintFactory {
    AttributeVector::SP _attr;
    Value::UP           _query;
    uint32_t            _target_hits;

public:
    EnnBlueprintFactory(AttributeVector::SP attr, Value::UP query, uint32_t target_hits);
    ~EnnBlueprintFactory() override;
    std::unique_ptr<Blueprint> make_blueprint() override;
    std::string get_name(Blueprint& blueprint) const override;
};

}
