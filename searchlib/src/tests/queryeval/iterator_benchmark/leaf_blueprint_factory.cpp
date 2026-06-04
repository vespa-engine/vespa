// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "leaf_blueprint_factory.h"

#include "attribute_ctx_builder.h"

#include <vespa/eval/eval/simple_value.h>
#include <vespa/eval/eval/tensor_spec.h>
#include <vespa/eval/eval/value_type.h>
#include <vespa/searchcommon/attribute/config.h>
#include <vespa/searchlib/common/bitvector.h>
#include <vespa/searchlib/queryeval/nearest_neighbor_blueprint.h>
#include <vespa/vespalib/util/fast_range.h>
#include <vespa/vespalib/util/require.h>
#include <vespa/vespalib/util/xoshiro.h>

#include <format>
#include <limits>
#include <random>

using search::attribute::BasicType;
using search::attribute::Config;
using vespalib::map_random_to_range;
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

} // namespace search::queryeval::test
