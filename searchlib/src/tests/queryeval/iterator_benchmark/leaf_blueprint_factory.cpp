// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "leaf_blueprint_factory.h"

#include <vespa/searchlib/queryeval/nearest_neighbor_blueprint.h>

namespace search::queryeval::test {

// ---------------- EnnBlueprintFactory --------------------

EnnBlueprintFactory::EnnBlueprintFactory(AttributeVector::SP attr, Value::UP query, uint32_t target_hits)
    : _attr(attr), _query(std::move(query)), _target_hits(target_hits) {
}

EnnBlueprintFactory::~EnnBlueprintFactory() = default;

std::unique_ptr<Blueprint> EnnBlueprintFactory::make_blueprint() {
    auto      calc = std::make_unique<tensor::DistanceCalculator>(*_attr->asTensorAttribute(), *_query);
    FieldSpec field("nn", 0, 0);
    return std::make_unique<NearestNeighborBlueprint>(field, std::move(calc), _target_hits, false,
                                                      NearestNeighborBlueprint::HnswParams{});
}

std::string EnnBlueprintFactory::get_name(Blueprint& blueprint) const {
    return get_class_name(blueprint);
}

} // namespace search::queryeval::test
