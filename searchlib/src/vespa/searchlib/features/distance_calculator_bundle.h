// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/fef/handle.h>
#include <vespa/vespalib/stllike/string.h>
#include <memory>
#include <vector>

namespace search::tensor { class DistanceCalculator; }
namespace search::fef { class IQueryEnvironment; }

namespace search::features {

/**
 * A bundle of term-field tuples (TermFieldHandle, DistanceCalculator) used by the closeness and distance rank features.
 *
 * For most document ids the raw score is available in the TermFieldMatchData retrieved using the TermFieldHandle,
 * as it was calculated during matching. In the other cases the DistanceCalculator can be used to calculate the score on the fly.
 */
class DistanceCalculatorBundle {
public:
    struct Element {
        fef::TermFieldHandle handle;
        std::unique_ptr<search::tensor::DistanceCalculator> calc;
        Element(Element&& rhs) noexcept = default; // Needed as std::vector::reserve() is used.
        Element(fef::TermFieldHandle handle_in);
        ~Element();
    };
private:
    std::vector<Element> _elems;

public:
    DistanceCalculatorBundle(const fef::IQueryEnvironment& env, uint32_t field_id);
    DistanceCalculatorBundle(const fef::IQueryEnvironment& env, const vespalib::string& label);

    const std::vector<Element>& elements() const { return _elems; }
};

}
