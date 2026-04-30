// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "leaf_blueprint_factory.h"

#include <vespa/searchlib/queryeval/nearest_neighbor_blueprint.h>

namespace search::queryeval::test {

// ---------------- EnnBlueprintFactory --------------------

EnnBlueprintFactory::EnnBlueprintFactory(uint32_t target_hits)
    : _target_hits(target_hits) {
}

EnnBlueprintFactory::~EnnBlueprintFactory() = default;

std::unique_ptr<Blueprint> EnnBlueprintFactory::make_blueprint() {
    FieldSpec field;
    auto distance_calc = tensor::DistanceCalculator::make_with_validation();
    auto enn = std::make_unique<NearestNeighborBlueprint>(field, distance_calc, _target_hits, false, {});
    return std::move(enn);
}

std::string EnnBlueprintFactory::get_name(Blueprint& blueprint) const {
    return get_class_name(blueprint);
}

}
