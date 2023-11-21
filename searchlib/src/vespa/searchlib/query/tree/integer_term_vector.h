// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "term_vector.h"
#include <vector>

namespace search::query {

/*
 * Class for integer terms owned by a MultiTerm term node.
 * Weights are not stored, all terms have weight 1.
 */
class IntegerTermVector : public TermVector {
    std::vector<int64_t> _terms;
    mutable char         _scratchPad[24];
public:
    explicit IntegerTermVector(uint32_t sz);
    ~IntegerTermVector() override;
    void addTerm(vespalib::stringref, Weight) override;
    void addTerm(int64_t term, Weight weight) override;
    void addTerm(int64_t term);
    [[nodiscard]] StringAndWeight getAsString(uint32_t index) const override;
    [[nodiscard]] IntegerAndWeight getAsInteger(uint32_t index) const override;
    [[nodiscard]] Weight getWeight(uint32_t index) const override;
    [[nodiscard]] uint32_t size() const override;
};

}
