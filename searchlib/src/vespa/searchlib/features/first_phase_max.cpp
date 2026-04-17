// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "first_phase_max.h"

#include <vespa/searchlib/fef/objectstore.h>

#include <limits>
#include <memory>
#include <string>

using search::fef::AnyWrapper;

namespace {

const std::string key = "firstPhaseMax";

}

namespace search::features {

FirstPhaseMax::FirstPhaseMax() : _first_phase_max(-std::numeric_limits<feature_t>::infinity()) {
}

FirstPhaseMax::~FirstPhaseMax() = default;


FirstPhaseMax::FirstPhaseMax(FirstPhaseMax&&) = default;

feature_t FirstPhaseMax::get() const noexcept {
    return _first_phase_max;
}

void FirstPhaseMax::set(feature_t score) noexcept {
    _first_phase_max = score;
}

void FirstPhaseMax::make_shared_state(fef::IObjectStore& store) {
    if (store.get(key) == nullptr) {
        store.add(key, std::make_unique<AnyWrapper<FirstPhaseMax>>(FirstPhaseMax()));
    }
}

FirstPhaseMax* FirstPhaseMax::get_mutable_shared_state(fef::IObjectStore& store) {
    auto* wrapper = dynamic_cast<AnyWrapper<FirstPhaseMax>*>(store.get_mutable(key));
    if (wrapper != nullptr) {
        return &wrapper->getValue();
    }
    return nullptr;
}

const FirstPhaseMax* FirstPhaseMax::get_shared_state(const fef::IObjectStore& store) {
    auto* wrapper = dynamic_cast<const AnyWrapper<FirstPhaseMax>*>(store.get(key));
    if (wrapper != nullptr) {
        return &wrapper->getValue();
    }
    return nullptr;
}

}
