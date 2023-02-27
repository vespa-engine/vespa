// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/fef/handle.h>
#include <vespa/vespalib/stllike/string.h>
#include <memory>
#include <optional>
#include <vector>

namespace search::tensor { class DistanceCalculator; }
namespace search::fef {
class IObjectStore;
class IQueryEnvironment;
}

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
        Element(fef::TermFieldHandle handle_in) noexcept;
        Element(fef::TermFieldHandle handle_in, std::unique_ptr<search::tensor::DistanceCalculator> calc_in) noexcept;
        ~Element();
    };
private:
    std::vector<Element> _elems;

public:
    DistanceCalculatorBundle(const fef::IQueryEnvironment& env,
                             uint32_t field_id,
                             const vespalib::string& feature_name);

    DistanceCalculatorBundle(const fef::IQueryEnvironment& env,
                             std::optional<uint32_t> field_id,
                             const vespalib::string& label,
                             const vespalib::string& feature_name);

    const std::vector<Element>& elements() const { return _elems; }

    static void prepare_shared_state(const fef::IQueryEnvironment& env,
                                     fef::IObjectStore& store,
                                     uint32_t field_id,
                                     const vespalib::string& feature_name);

    static void prepare_shared_state(const fef::IQueryEnvironment& env,
                                     fef::IObjectStore& store,
                                     const vespalib::string& label,
                                     const vespalib::string& feature_name);
};

}
