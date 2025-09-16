// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "term_vector.h"
#include <vector>

namespace search::query {

/*
 * Class for weighted string terms owned by a MultiTerm term node.
 */
class WeightedStringTermVector final : public TermVector {
public:
    explicit WeightedStringTermVector(uint32_t sz);
    ~WeightedStringTermVector() override;
    void addTerm(std::string_view term, Weight weight) override;
    void addTerm(int64_t value, Weight weight) override;
    [[nodiscard]] StringAndWeight getAsString(uint32_t index) const override;
    [[nodiscard]] IntegerAndWeight getAsInteger(uint32_t index) const override;
    [[nodiscard]] Weight getWeight(uint32_t index) const override;
    [[nodiscard]] uint32_t size() const override;
private:
    std::vector<std::pair<std::string, Weight>> _terms;
};

}
