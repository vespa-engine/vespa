// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "leaf_blueprint_factory.h"

#include "attribute_ctx_builder.h"

#include <vespa/eval/eval/simple_value.h>
#include <vespa/eval/eval/tensor_spec.h>
#include <vespa/eval/eval/value_type.h>
#include <vespa/searchcommon/attribute/config.h>
#include <vespa/searchlib/common/bitvector.h>
#include <vespa/searchlib/query/tree/simplequery.h>
#include <vespa/searchlib/queryeval/nearest_neighbor_blueprint.h>
#include <vespa/vespalib/util/fast_range.h>
#include <vespa/vespalib/util/require.h>
#include <vespa/vespalib/util/stride.h>
#include <vespa/vespalib/util/xoshiro.h>

#include <format>
#include <limits>
#include <random>

using search::attribute::BasicType;
using search::attribute::Config;
using search::query::Range;
using search::query::SimpleRangeTerm;
using search::query::Weight;
using vespalib::map_random_to_range;
using vespalib::Stride;
using vespalib::Xoshiro256PlusPlusPrng;
using vespalib::eval::SimpleValue;
using vespalib::eval::TensorSpec;
using vespalib::eval::Value;
using vespalib::eval::ValueType;

namespace search::queryeval::test {

namespace {

Value::UP make_random_vec(const std::string& type_spec, uint32_t dim, Xoshiro256PlusPlusPrng& gen) {
    std::uniform_real_distribution<float> dist(-1.0f, 1.0f);
    TensorSpec                            spec(type_spec);
    for (uint32_t i = 0; i < dim; i++) {
        spec.add({{"x", i}}, dist(gen));
    }
    return SimpleValue::from_spec(spec);
}

} // namespace

// ---------------- EnnBlueprintFactory --------------------

EnnBlueprintFactory::EnnBlueprintFactory(const EnnConfig& cfg)
    : _attr(), _query(), _target_hits(cfg.target_hits), _global_filter(), _global_filter_hit_ratio(0.0) {
    auto   type_spec = std::format("tensor<float>(x[{}])", cfg.dim);
    Config tensor_cfg(BasicType::TENSOR);
    tensor_cfg.setTensorType(ValueType::from_spec(type_spec));
    tensor_cfg.set_distance_metric(cfg.distance_metric);

    Xoshiro256PlusPlusPrng  gen(cfg.seed);
    AttributeContextBuilder builder;
    _attr = builder.add_tensor(tensor_cfg, "nn", cfg.num_docs,
                               [&](uint32_t) { return make_random_vec(type_spec, cfg.dim, gen); });
    _query = make_random_vec(type_spec, cfg.dim, gen);

    if (cfg.global_filter_hit_ratio.has_value()) {
        _global_filter_hit_ratio = cfg.global_filter_hit_ratio.value();
        uint64_t docs_left = cfg.num_docs;
        uint32_t wanted_hits = docs_left * _global_filter_hit_ratio;
        uint32_t hits_left = wanted_hits;
        auto     bits = BitVector::create(1, cfg.num_docs + 1);
        for (uint32_t docid = 1; docid <= cfg.num_docs; ++docid) {
            if (map_random_to_range(gen(), docs_left) < hits_left) {
                bits->setBit(docid);
                --hits_left;
            }
            --docs_left;
        }
        bits->invalidateCachedCount();
        REQUIRE_EQ(bits->countTrueBits(), wanted_hits);
        _global_filter = GlobalFilter::create(std::move(bits));
    }
}

EnnBlueprintFactory::~EnnBlueprintFactory() = default;

std::unique_ptr<Blueprint> EnnBlueprintFactory::make_blueprint() {
    auto      calc = std::make_unique<tensor::DistanceCalculator>(*_attr->asTensorAttribute(), *_query);
    FieldSpec field("nn", 0, 0);
    NearestNeighborBlueprint::HnswParams hnsw_params{};
    hnsw_params.distance_threshold = std::numeric_limits<double>::max();
    auto bp = std::make_unique<NearestNeighborBlueprint>(field, std::move(calc), _target_hits, false, hnsw_params);
    if (_global_filter) {
        bp->set_global_filter(*_global_filter, _global_filter_hit_ratio);
    }
    return bp;
}

std::string EnnBlueprintFactory::get_name(Blueprint& blueprint) const {
    return get_class_name(blueprint);
}

// ---------------- AttributeRangeBlueprintFactory --------------------

AttributeRangeBlueprintFactory::AttributeRangeBlueprintFactory(const RangeConfig& cfg)
    : _range_low(cfg.range_low), _range_high(cfg.range_low), _range_size(cfg.range_size), _searchable() {
    REQUIRE(cfg.field_cfg.is_attr());
    REQUIRE(cfg.target_hits > 0);
    REQUIRE(cfg.target_hits <= cfg.num_docs);
    REQUIRE(cfg.range_size > 0);
    _range_high = cfg.range_high();
    REQUIRE(cfg.uncommon_value < _range_low || cfg.uncommon_value > _range_high);

    Xoshiro256PlusPlusPrng  gen(cfg.seed);
    Stride                  stride(cfg.num_docs, static_cast<uint32_t>(cfg.target_hits));
    uint32_t                next_docid = 1;
    uint32_t                hits_generated = 0;
    AttributeContextBuilder builder;
    builder.add_integer(
        cfg.field_cfg.attr_cfg(), "range_attr", cfg.num_docs, [&](uint32_t docid) noexcept -> int64_t {
            if (docid == next_docid) {
                next_docid += stride.next();
                ++hits_generated;
                int64_t value = map_random_to_range(gen(), static_cast<uint64_t>(_range_size)) + _range_low;
                return value;
            }
            return cfg.uncommon_value;
        });

    REQUIRE_EQ(hits_generated, cfg.target_hits);

    _searchable = builder.build();
}

AttributeRangeBlueprintFactory::~AttributeRangeBlueprintFactory() = default;

std::unique_ptr<Blueprint> AttributeRangeBlueprintFactory::make_blueprint() {
    SimpleRangeTerm term(query::Range(_range_low, _range_high), "range_attr", 0, Weight(1));
    return _searchable->create_blueprint(FieldSpec("range_attr", 0, 0), term);
}

std::string AttributeRangeBlueprintFactory::get_name(Blueprint& blueprint) const {
    return get_class_name(blueprint);
}

} // namespace search::queryeval::test
