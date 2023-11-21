// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "term_vector.h"
#include <vector>

namespace search::query {

/*
 * Class for string terms owned by a MultiTerm term node.
 * Weights are not stored, all terms have weight 1.
 */
class StringTermVector : public TermVector {
    std::vector<vespalib::string> _terms;
public:
    explicit StringTermVector(uint32_t sz);
    ~StringTermVector() override;
    void addTerm(vespalib::stringref term, Weight weight) override;
    void addTerm(int64_t term, Weight weight) override;
    void addTerm(vespalib::stringref term);
    [[nodiscard]] StringAndWeight getAsString(uint32_t index) const override;
    [[nodiscard]] IntegerAndWeight getAsInteger(uint32_t index) const override;
    [[nodiscard]] Weight getWeight(uint32_t index) const override;
    [[nodiscard]] uint32_t size() const override;
};

}
