// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/common/feature.h>

namespace search::fef { class IObjectStore; }

namespace search::features {

/**
 * This class stores and retrieves the max first phase score for use in second
 * phase.
 */
class FirstPhaseMax {
    feature_t _first_phase_max;

public:
    FirstPhaseMax();
    FirstPhaseMax(const FirstPhaseMax&) = delete;
    FirstPhaseMax(FirstPhaseMax&&);
    ~FirstPhaseMax();

    FirstPhaseMax& operator=(const FirstPhaseMax&) = delete;
    FirstPhaseMax& operator=(FirstPhaseMax&&) = delete;

    feature_t get() const noexcept;
    void set(feature_t score) noexcept;
    static void make_shared_state(fef::IObjectStore& store);
    static FirstPhaseMax* get_mutable_shared_state(fef::IObjectStore& store);
    static const FirstPhaseMax* get_shared_state(const fef::IObjectStore& store);
};

}
