// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "benchmark_blueprint_factory.h"

#include <vespa/eval/eval/value.h>
#include <vespa/searchcommon/attribute/distance_metric.h>
#include <vespa/searchlib/attribute/attributevector.h>

namespace search::queryeval::test {

struct EnnConfig {
    using DistanceMetric = search::attribute::DistanceMetric;

    uint32_t       num_docs = 10'000;
    uint32_t       dim = 2;
    uint32_t       target_hits = 100;
    DistanceMetric distance_metric = DistanceMetric::Euclidean;
    uint32_t       seed = 42;
};

/**
 * Factory that creates an ENN blueprint.
 */
class EnnBlueprintFactory : public BenchmarkBlueprintFactory {
public:
    using Value = vespalib::eval::Value;

private:
    AttributeVector::SP       _attr;
    vespalib::eval::Value::UP _query;
    uint32_t                  _target_hits;

public:
    explicit EnnBlueprintFactory(const EnnConfig& cfg);
    ~EnnBlueprintFactory() override;
    std::unique_ptr<Blueprint> make_blueprint() override;
    std::string get_name(Blueprint& blueprint) const override;
};

} // namespace search::queryeval::test
