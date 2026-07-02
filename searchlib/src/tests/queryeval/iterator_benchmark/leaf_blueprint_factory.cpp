// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "leaf_blueprint_factory.h"

#include "attribute_ctx_builder.h"

#include <vespa/document/base/globalid.h>
#include <vespa/eval/eval/simple_value.h>
#include <vespa/eval/eval/tensor_spec.h>
#include <vespa/eval/eval/value_type.h>
#include <vespa/searchcommon/attribute/config.h>
#include <vespa/searchcommon/attribute/i_document_meta_store_context.h>
#include <vespa/searchlib/attribute/attribute_read_guard.h>
#include <vespa/searchlib/attribute/attributefactory.h>
#include <vespa/searchlib/attribute/imported_attribute_vector.h>
#include <vespa/searchlib/attribute/imported_attribute_vector_factory.h>
#include <vespa/searchlib/attribute/integerbase.h>
#include <vespa/searchlib/attribute/reference_attribute.h>
#include <vespa/searchlib/common/bitvector.h>
#include <vespa/searchlib/query/tree/simplequery.h>
#include <vespa/searchlib/queryeval/nearest_neighbor_blueprint.h>
#include <vespa/searchlib/test/mock_gid_to_lid_mapping.h>
#include <vespa/vespalib/util/fast_range.h>
#include <vespa/vespalib/util/require.h>
#include <vespa/vespalib/util/stride.h>
#include <vespa/vespalib/util/xoshiro.h>

#include <cstdlib>
#include <cstring>
#include <format>
#include <limits>
#include <random>

using search::attribute::BasicType;
using search::attribute::Config;
using search::attribute::IAttributeVector;
using search::attribute::ImportedAttributeVectorFactory;
using search::attribute::ReferenceAttribute;
using search::attribute::test::MockGidToLidMapperFactory;
using search::query::Range;
using search::query::SimpleRangeTerm;
using search::query::SimpleStringTerm;
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
    SimpleRangeTerm term(query::Range(_range_low, _range_high), "range_attr", 0, Weight(100));
    return _searchable->create_blueprint(FieldSpec("range_attr", 0, 0), term);
}

std::string AttributeRangeBlueprintFactory::get_name(Blueprint& blueprint) const {
    return get_class_name(blueprint);
}

// ---------------- ImportedAttributeBlueprintFactory --------------------

namespace {

const std::string imported_field_name = "imported_attr";

/**
 * Minimal document meta store context. The imported attribute holds on to read guards from it,
 * but never dereferences them as long as the bitvector search cache is disabled.
 */
struct BenchmarkDocumentMetaStoreContext : search::IDocumentMetaStoreContext {
    struct Guard : IReadGuard {
        const search::IDocumentMetaStore& get() const override { abort(); }
    };
    IReadGuard::SP getReadGuard() const override { return std::make_shared<Guard>(); }
};

document::GlobalId make_gid(uint32_t value) {
    char buf[document::GlobalId::LENGTH];
    memset(buf, 0, sizeof(buf));
    memcpy(buf, &value, sizeof(value));
    return document::GlobalId(buf);
}

} // namespace

ImportedAttributeBlueprintFactory::ImportedAttributeBlueprintFactory(const ImportedConfig& cfg)
    : _match_value(cfg.match_value), _imported_attr(), _searchable() {
    REQUIRE(cfg.field_cfg.is_attr());
    REQUIRE(cfg.num_target_docs > 0);
    REQUIRE(cfg.target_hits() > 0);
    REQUIRE(cfg.match_value != cfg.non_match_value);

    // Target (parent) attribute, populated such that target_hits() documents match the query term.
    auto target_attr = search::AttributeFactory::createAttribute("target", cfg.field_cfg.attr_cfg());
    target_attr->addReservedDoc();
    target_attr->addDocs(cfg.num_target_docs);
    auto&    typed_target = dynamic_cast<IntegerAttribute&>(*target_attr);
    Stride   stride(cfg.num_target_docs, cfg.target_hits());
    uint32_t next_hit_lid = 1;
    uint32_t hits_generated = 0;
    for (uint32_t target_lid = 1; target_lid <= cfg.num_target_docs; ++target_lid) {
        if (target_lid == next_hit_lid) {
            next_hit_lid += stride.next();
            ++hits_generated;
            typed_target.update(target_lid, cfg.match_value);
        } else {
            typed_target.update(target_lid, cfg.non_match_value);
        }
    }
    target_attr->commit(CommitParam::UpdateStats::FORCE);
    REQUIRE_EQ(hits_generated, cfg.target_hits());

    // Reference (child) attribute, mapping each child document to a uniformly random parent document.
    auto reference_attr = std::make_shared<ReferenceAttribute>("ref");
    auto mapper_factory = std::make_shared<MockGidToLidMapperFactory>();
    reference_attr->setGidToLidMapperFactory(mapper_factory);
    reference_attr->addDocs(cfg.num_docs + 1); // including the reserved lid 0
    for (uint32_t target_lid = 1; target_lid <= cfg.num_target_docs; ++target_lid) {
        auto gid = make_gid(target_lid);
        mapper_factory->_map[gid] = target_lid;
        reference_attr->notifyReferencedPutNoCommit(gid, target_lid);
    }
    Xoshiro256PlusPlusPrng gen(cfg.seed);
    for (uint32_t child_lid = 1; child_lid <= cfg.num_docs; ++child_lid) {
        uint32_t target_lid = map_random_to_range(gen(), static_cast<uint64_t>(cfg.num_target_docs)) + 1;
        reference_attr->update(child_lid, make_gid(target_lid));
    }
    reference_attr->commit();

    _imported_attr = ImportedAttributeVectorFactory::create(
        imported_field_name, std::move(reference_attr), std::make_shared<BenchmarkDocumentMetaStoreContext>(),
        std::move(target_attr), std::make_shared<BenchmarkDocumentMetaStoreContext>(), /*use_search_cache=*/false);

    // The read guard is the IAttributeVector implementation seen by the query machinery.
    // Note: attribute:: qualification is required; the legacy search::AttributeReadGuard shadows it here.
    auto guard = std::shared_ptr<attribute::AttributeReadGuard>(_imported_attr->makeReadGuard(false));
    std::shared_ptr<const IAttributeVector> attr_view(guard, guard->attribute());
    AttributeContextBuilder                 builder;
    builder.add(std::move(attr_view));
    _searchable = builder.build();
}

ImportedAttributeBlueprintFactory::~ImportedAttributeBlueprintFactory() = default;

std::unique_ptr<Blueprint> ImportedAttributeBlueprintFactory::make_blueprint() {
    SimpleStringTerm term(std::to_string(_match_value), imported_field_name, 0, Weight(1));
    return _searchable->create_blueprint(FieldSpec(imported_field_name, 0, 0), term);
}

std::string ImportedAttributeBlueprintFactory::get_name(Blueprint& blueprint) const {
    return get_class_name(blueprint);
}

} // namespace search::queryeval::test
