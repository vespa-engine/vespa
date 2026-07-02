// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "benchmark_blueprint_factory.h"
#include "benchmark_searchable.h"

#include <vespa/eval/eval/value.h>
#include <vespa/searchcommon/attribute/distance_metric.h>
#include <vespa/searchlib/attribute/attributevector.h>
#include <vespa/searchlib/queryeval/global_filter.h>

#include <memory>
#include <optional>

namespace search::attribute {
class ImportedAttributeVector;
}

namespace search::queryeval::test {

struct EnnConfig {
    using DistanceMetric = search::attribute::DistanceMetric;

    uint32_t              num_docs = 10'000;
    uint32_t              dim = 64;
    uint32_t              target_hits = 100;
    DistanceMetric        distance_metric = DistanceMetric::Euclidean;
    uint32_t              seed = 42;
    std::optional<double> global_filter_hit_ratio = std::nullopt;
};

/**
 * Factory that creates an ENN blueprint.
 */
class EnnBlueprintFactory : public BenchmarkBlueprintFactory {
public:
    using Value = vespalib::eval::Value;

private:
    AttributeVector::SP           _attr;
    Value::UP                     _query;
    uint32_t                      _target_hits;
    std::shared_ptr<GlobalFilter> _global_filter;
    double                        _global_filter_hit_ratio;

public:
    explicit EnnBlueprintFactory(const EnnConfig& cfg);
    ~EnnBlueprintFactory() override;
    std::unique_ptr<Blueprint> make_blueprint() override;
    std::string get_name(Blueprint& blueprint) const override;
};

/**
 * Configure an attribute with range search.
 */
struct RangeConfig {
    FieldConfig field_cfg;
    int64_t     target_hits;
    int64_t     range_size;
    int64_t     range_low = 128;
    uint32_t    num_docs = 10'000;
    int64_t     uncommon_value = 2;
    uint32_t    seed = 42;

    // inclusive range [range_low, range_high()] contains range_size elements.
    [[nodiscard]] int64_t range_high() const noexcept { return range_low + range_size - 1; }
};

/**
 * Factory that creates an attribute search with range search.
 */
class AttributeRangeBlueprintFactory : public BenchmarkBlueprintFactory {
    int64_t                              _range_low;
    int64_t                              _range_high;
    int64_t                              _range_size;
    std::unique_ptr<BenchmarkSearchable> _searchable;

public:
    explicit AttributeRangeBlueprintFactory(const RangeConfig& cfg);
    ~AttributeRangeBlueprintFactory() override;
    std::unique_ptr<Blueprint> make_blueprint() override;
    std::string get_name(Blueprint& blueprint) const override;
};

/**
 * Configure a term search in an imported attribute (parent-child).
 */
struct ImportedConfig {
    // Config for parent relation.
    FieldConfig field_cfg;

    // Config for child.
    uint32_t num_docs = 10'000;
    uint32_t num_target_docs = 1'000;
    double   target_hit_ratio = 0.1;
    int64_t  match_value = 100;
    int64_t  non_match_value = 2;
    uint32_t seed = 42;

    [[nodiscard]] uint32_t target_hits() const noexcept {
        return static_cast<uint32_t>(target_hit_ratio * num_target_docs);
    }
};

/**
 * Factory that creates a term search blueprint over an imported attribute.
 */
class ImportedAttributeBlueprintFactory : public BenchmarkBlueprintFactory {
public:
    using ImportedAttributeVector = search::attribute::ImportedAttributeVector;

private:
    int64_t                                  _match_value;
    std::shared_ptr<ImportedAttributeVector> _imported_attr;
    std::unique_ptr<BenchmarkSearchable>     _searchable;

public:
    explicit ImportedAttributeBlueprintFactory(const ImportedConfig& cfg);
    ~ImportedAttributeBlueprintFactory() override;
    std::unique_ptr<Blueprint> make_blueprint() override;
    std::string get_name(Blueprint& blueprint) const override;
};

} // namespace search::queryeval::test
